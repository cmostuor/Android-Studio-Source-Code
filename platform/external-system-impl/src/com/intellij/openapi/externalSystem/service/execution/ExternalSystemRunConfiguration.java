package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 18:30
 */
public class ExternalSystemRunConfiguration extends LocatableConfigurationBase {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemRunConfiguration.class.getName());

  private ExternalSystemTaskExecutionSettings mySettings = new ExternalSystemTaskExecutionSettings();

  public ExternalSystemRunConfiguration(@NotNull ProjectSystemId externalSystemId,
                                        Project project,
                                        ConfigurationFactory factory,
                                        String name) {
    super(project, factory, name);
    mySettings.setExternalSystemIdString(externalSystemId.getId());
  }

  @Override
  public String suggestedName() {
    return AbstractExternalSystemTaskConfigurationType.generateName(getProject(), mySettings);
  }

  @Override
  public RunConfiguration clone() {
    ExternalSystemRunConfiguration result = (ExternalSystemRunConfiguration)super.clone();
    result.mySettings = mySettings.clone();
    return result;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    Element e = element.getChild(ExternalSystemTaskExecutionSettings.TAG_NAME);
    if (e != null) {
      mySettings = XmlSerializer.deserialize(e, ExternalSystemTaskExecutionSettings.class);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings));
  }

  @NotNull
  public ExternalSystemTaskExecutionSettings getSettings() {
    return mySettings;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new ExternalSystemRunConfigurationEditor(getProject(), mySettings.getExternalSystemId());
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new MyRunnableState(mySettings, getProject(), DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()), this, env);
  }

  public static class MyRunnableState implements RunProfileState {

    @NotNull private final ExternalSystemTaskExecutionSettings mySettings;
    @NotNull private final Project myProject;
    @NotNull private final ExternalSystemRunConfiguration myConfiguration;
    @NotNull private final ExecutionEnvironment myEnv;

    private final int myDebugPort;

    public MyRunnableState(@NotNull ExternalSystemTaskExecutionSettings settings,
                           @NotNull Project project,
                           boolean debug,
                           @NotNull ExternalSystemRunConfiguration configuration,
                           @NotNull ExecutionEnvironment env) {
      mySettings = settings;
      myProject = project;
      myConfiguration = configuration;
      myEnv = env;
      int port;
      if (debug) {
        try {
          port = NetUtils.findAvailableSocketPort();
        }
        catch (IOException e) {
          LOG.warn("Unexpected I/O exception occurred on attempt to find a free port to use for external system task debugging", e);
          port = 0;
        }
      }
      else {
        port = 0;
      }
      myDebugPort = port;
    }

    public int getDebugPort() {
      return myDebugPort;
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
      if (myProject.isDisposed()) return null;

      ExternalSystemUtil.updateRecentTasks(new ExternalTaskExecutionInfo(mySettings.clone(), executor.getId()), myProject);
      final List<ExternalTaskPojo> tasks = ContainerUtilRt.newArrayList();
      for (String taskName : mySettings.getTaskNames()) {
        tasks.add(new ExternalTaskPojo(taskName, mySettings.getExternalProjectPath(), null));
      }
      if (tasks.isEmpty()) {
        throw new ExecutionException(ExternalSystemBundle.message("run.error.undefined.task"));
      }
      String debuggerSetup = null;
      if (myDebugPort > 0) {
        debuggerSetup = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + myDebugPort;
      }

      ApplicationManager.getApplication().assertIsDispatchThread();
      FileDocumentManager.getInstance().saveAllDocuments();

      final ExternalSystemExecuteTaskTask task = new ExternalSystemExecuteTaskTask(mySettings.getExternalSystemId(),
                                                                                   myProject,
                                                                                   tasks,
                                                                                   mySettings.getVmOptions(),
                                                                                   mySettings.getScriptParameters(),
                                                                                   debuggerSetup);

      final MyProcessHandler processHandler = new MyProcessHandler(task);
      final ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration> consoleManager = getConsoleManagerFor(task);

      final ExecutionConsole consoleView =
        consoleManager.attachExecutionConsole(task, myProject, myConfiguration, executor, myEnv, processHandler);
      Disposer.register(myProject, consoleView);

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final String startDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
          final String greeting;
          if (mySettings.getTaskNames().size() > 1) {
            greeting = ExternalSystemBundle
              .message("run.text.starting.multiple.task", startDateTime, StringUtil.join(mySettings.getTaskNames(), " "));
          }
          else {
            greeting =
              ExternalSystemBundle.message("run.text.starting.single.task", startDateTime, StringUtil.join(mySettings.getTaskNames(), " "));
          }
          processHandler.notifyTextAvailable(greeting, ProcessOutputTypes.SYSTEM);
          task.execute(new ExternalSystemTaskNotificationListenerAdapter() {

            private boolean myResetGreeting = true;

            @Override
            public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
              if (myResetGreeting) {
                processHandler.notifyTextAvailable("\r", ProcessOutputTypes.SYSTEM);
                myResetGreeting = false;
              }

              consoleManager.onOutput(text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
            }

            @Override
            public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
              String exceptionMessage = ExceptionUtil.getMessage(e);
              String text = exceptionMessage == null ? e.toString() : exceptionMessage;
              processHandler.notifyTextAvailable(text + '\n', ProcessOutputTypes.STDERR);
              processHandler.notifyProcessTerminated(1);
            }

            @Override
            public void onEnd(@NotNull ExternalSystemTaskId id) {
              final String endDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
              final String farewell;
              if (mySettings.getTaskNames().size() > 1) {
                farewell = ExternalSystemBundle
                  .message("run.text.ended.multiple.task", endDateTime, StringUtil.join(mySettings.getTaskNames(), " "));
              }
              else {
                farewell =
                  ExternalSystemBundle.message("run.text.ended.single.task", endDateTime, StringUtil.join(mySettings.getTaskNames(), " "));
              }
              processHandler.notifyTextAvailable(farewell, ProcessOutputTypes.SYSTEM);
              processHandler.notifyProcessTerminated(0);
            }
          });
        }
      });
      DefaultExecutionResult result = new DefaultExecutionResult(consoleView, processHandler);
      result.setRestartActions(consoleManager.getRestartActions());
      return result;
    }
  }

  private static class MyProcessHandler extends ProcessHandler {
    private final ExternalSystemExecuteTaskTask myTask;
    @Nullable private volatile OutputStream myOutputStream;

    public MyProcessHandler(ExternalSystemExecuteTaskTask task) {
      myTask = task;
    }

    @Override
    protected void destroyProcessImpl() {
    }

    @Override
    protected void detachProcessImpl() {
      myTask.cancel(new ExternalSystemTaskNotificationListenerAdapter() {

        private boolean myResetGreeting = true;

        @Override
        public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
          if (myResetGreeting) {
            notifyTextAvailable("\r", ProcessOutputTypes.SYSTEM);
            myResetGreeting = false;
          }
          notifyTextAvailable(text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
        }
      });
      notifyProcessDetached();
    }

    @Override
    public boolean detachIsDefault() {
      return true;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return null;
    }

    @Override
    public void notifyProcessTerminated(int exitCode) {
      super.notifyProcessTerminated(exitCode);
    }
  }

  @NotNull
  private static ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration> getConsoleManagerFor(@NotNull ExternalSystemTask task) {
    for (ExternalSystemExecutionConsoleManager executionConsoleManager : ExternalSystemExecutionConsoleManager.EP_NAME.getExtensions()) {
      if (executionConsoleManager.isApplicableFor(task))
        //noinspection unchecked
        return executionConsoleManager;
    }

    return new DefaultExternalSystemExecutionConsoleManager();
  }

}
