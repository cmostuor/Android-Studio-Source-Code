package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;

public class Patch {
  private List<PatchAction> myActions = new ArrayList<PatchAction>();
  private boolean myIsBinary;
  private boolean myIsStrict;
  private String myOldBuild;
  private String myNewBuild;

  private static final int CREATE_ACTION_KEY = 1;
  private static final int UPDATE_ACTION_KEY = 2;
  private static final int UPDATE_ZIP_ACTION_KEY = 3;
  private static final int DELETE_ACTION_KEY = 4;
  private static final int VALIDATE_ACTION_KEY = 5;
  private static final int MULTI_ACTION_KEY = 6;

  public Patch(PatchSpec spec, UpdaterUI ui) throws IOException, OperationCancelledException {
    myIsBinary = spec.isBinary();
    myIsStrict = spec.isStrict();
    myOldBuild = spec.getOldVersionDescription();
    myNewBuild = spec.getNewVersionDescription();

    calculateActions(spec, ui);
  }

  public Patch(InputStream patchIn) throws IOException {
    read(patchIn);
  }

  private void calculateActions(PatchSpec spec, UpdaterUI ui) throws IOException, OperationCancelledException {
    Runner.logger.info("Calculating difference...");
    ui.startProcess("Calculating difference...");
    ui.checkCancelled();

    File newerDir = new File(spec.getNewFolder());
    List<PatchAction> actions = null;
    for (String olderPath : spec.getOldFolders()) {
      File olderDir = new File(olderPath);
      List<PatchAction> newActions = createActionsFor(olderDir, newerDir, spec, ui);
      actions = mergeActions(actions, newActions);
    }
    myActions = actions;
  }

  private List<PatchAction> mergeActions(List<PatchAction> actions, List<PatchAction> newActions) {
    List<PatchAction> merged = new LinkedList<PatchAction>();
    if (actions == null) return newActions;

    Map<String, PatchAction> actionsToAdd = new LinkedHashMap<String, PatchAction>();
    for (PatchAction action : newActions) {
      actionsToAdd.put(action.getPath(), action);
    }

    for (PatchAction action : actions) {
      PatchAction other = actionsToAdd.remove(action.getPath());
      merged.add(other != null ? action.merge(other) : action);
    }

    merged.addAll(actionsToAdd.values());

    return merged;
  }

  private List<PatchAction> createActionsFor(File olderDir, File newerDir, PatchSpec spec, UpdaterUI ui)
    throws IOException, OperationCancelledException {
    DiffCalculator.Result diff;
    diff = DiffCalculator.calculate(digestFiles(olderDir, spec.getIgnoredFiles(), ui), digestFiles(newerDir, spec.getIgnoredFiles(), ui),
                                    true);

    List<PatchAction> tempActions = new ArrayList<PatchAction>();

    // 'delete' actions before 'create' actions to prevent newly created files to be deleted if the names differ only on case.
    for (Map.Entry<String, Long> each : diff.filesToDelete.entrySet()) {
      // Add them in reverse order so directory structures start deleting the files before the directory itself.
      tempActions.add(0, new DeleteAction(this, each.getKey(), each.getValue()));
    }

    for (String each : diff.filesToCreate.keySet()) {
      tempActions.add(new CreateAction(this, each));
    }

    for (Map.Entry<String, DiffCalculator.Update> each : diff.filesToUpdate.entrySet()) {
      DiffCalculator.Update update = each.getValue();
      if (!spec.isBinary() && Utils.isZipFile(each.getKey())) {
        tempActions.add(new UpdateZipAction(this, olderDir, each.getKey(), update.source, update.checksum, update.move));
      }
      else {
        tempActions.add(new UpdateAction(this, olderDir, each.getKey(), update.source, update.checksum, update.move));
      }
    }

    if (spec.isStrict()) {
      for (Map.Entry<String, Long> each : diff.commonFiles.entrySet()) {
        tempActions.add(new ValidateAction(this, each.getKey(), each.getValue()));
      }
    }

    Runner.logger.info("Preparing actions...");
    ui.startProcess("Preparing actions...");
    ui.checkCancelled();

    List<PatchAction> actions = new ArrayList<PatchAction>();
    for (PatchAction each : tempActions) {
      ui.setStatus(each.getPath());
      ui.checkCancelled();

      if (!each.calculate(olderDir, newerDir)) continue;
      actions.add(each);
      each.setCritical(spec.getCriticalFiles().contains(each.getPath()));
      each.setOptional(spec.getOptionalFiles().contains(each.getPath()));
    }

    return actions;
  }

  public List<PatchAction> getActions() {
    return myActions;
  }

  public void write(OutputStream out) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream dataOut = new DataOutputStream(out);
    try {
      dataOut.writeUTF(myOldBuild);
      dataOut.writeUTF(myNewBuild);
      dataOut.writeBoolean(myIsBinary);
      dataOut.writeBoolean(myIsStrict);
      writeActions(dataOut, myActions);
    }
    finally {
      dataOut.flush();
    }
  }

  public void writeActions(DataOutputStream dataOut, List<PatchAction> actions) throws IOException {
    dataOut.writeInt(actions.size());

    for (PatchAction each : actions) {
      int key;
      Class clazz = each.getClass();

      if (clazz == CreateAction.class) {
        key = CREATE_ACTION_KEY;
      }
      else if (clazz == UpdateAction.class) {
        key = UPDATE_ACTION_KEY;
      }
      else if (clazz == UpdateZipAction.class) {
        key = UPDATE_ZIP_ACTION_KEY;
      }
      else if (clazz == DeleteAction.class) {
        key = DELETE_ACTION_KEY;
      }
      else if (clazz == ValidateAction.class) {
        key = VALIDATE_ACTION_KEY;
      }
      else if (clazz == MultiAction.class) {
        key = MULTI_ACTION_KEY;
      }
      else {
        throw new RuntimeException("Unknown action " + each);
      }
      dataOut.writeInt(key);
      each.write(dataOut);
    }
  }

  private void read(InputStream patchIn) throws IOException {

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataInputStream in = new DataInputStream(patchIn);

    myOldBuild = in.readUTF();
    myNewBuild = in.readUTF();
    myIsBinary = in.readBoolean();
    myIsStrict = in.readBoolean();
    myActions = readActions(in);
  }

  public List<PatchAction> readActions(DataInputStream in) throws IOException {
    List<PatchAction> actions = new ArrayList<PatchAction>();
    int size = in.readInt();
    while (size-- > 0) {
      int key = in.readInt();
      PatchAction a;
      switch (key) {
        case CREATE_ACTION_KEY:
          a = new CreateAction(this, in);
          break;
        case UPDATE_ACTION_KEY:
          a = new UpdateAction(this, in);
          break;
        case UPDATE_ZIP_ACTION_KEY:
          a = new UpdateZipAction(this, in);
          break;
        case DELETE_ACTION_KEY:
          a = new DeleteAction(this, in);
          break;
        case VALIDATE_ACTION_KEY:
          a = new ValidateAction(this, in);
          break;
        case MULTI_ACTION_KEY:
          a = new MultiAction(this, in);
          break;
        default:
          throw new RuntimeException("Unknown action type " + key);
      }
      actions.add(a);
    }
    return actions;
  }

  public List<ValidationResult> validate(final File toDir, UpdaterUI ui) throws IOException, OperationCancelledException {
    final LinkedHashSet<String> files = Utils.collectRelativePaths(toDir, myIsStrict);
    final List<ValidationResult> result = new ArrayList<ValidationResult>();

    if (myIsStrict) {
      // In strict mode add delete actions for unknown files.
      for (PatchAction action : myActions) {
        files.remove(action.getPath());
      }
      for (String file : files) {
        myActions.add(0, new DeleteAction(this, file, Digester.INVALID));
      }
    }
    Runner.logger.info("Validating installation...");
    forEach(myActions, "Validating installation...", ui, true,
            new ActionsProcessor() {
              @Override
              public void forEach(PatchAction each) throws IOException {
                ValidationResult validationResult = each.validate(toDir);
                if (validationResult != null) result.add(validationResult);
              }
            });

    return result;
  }

  public ApplicationResult apply(final ZipFile patchFile,
                                 final File toDir,
                                 final File backupDir,
                                 final Map<String, ValidationResult.Option> options,
                                 UpdaterUI ui) throws IOException, OperationCancelledException {

    List<PatchAction> actionsToProcess = new ArrayList<PatchAction>();
    for (PatchAction each : myActions) {
      if (each.shouldApply(toDir, options)) actionsToProcess.add(each);
    }

    forEach(actionsToProcess, "Backing up files...", ui, true,
            new ActionsProcessor() {
              @Override
              public void forEach(PatchAction each) throws IOException {
                each.backup(toDir, backupDir);
              }
            });

    final List<PatchAction> appliedActions = new ArrayList<PatchAction>();
    boolean shouldRevert = false;
    boolean cancelled = false;
    try {
      forEach(actionsToProcess, "Applying patch...", ui, true,
              new ActionsProcessor() {
                @Override
                public void forEach(PatchAction each) throws IOException {
                  appliedActions.add(each);
                  each.apply(patchFile, backupDir, toDir);
                }
              });
    }
    catch (OperationCancelledException e) {
      Runner.printStackTrace(e);
      shouldRevert = true;
      cancelled = true;
    }
    catch (Throwable e) {
      Runner.printStackTrace(e);
      shouldRevert = true;
      ui.showError(e);
    }

    if (shouldRevert) {
      revert(appliedActions, backupDir, toDir, ui);
      appliedActions.clear();

      if (cancelled) throw new OperationCancelledException();
    }

    // on OS X we need to update bundle timestamp to reset Info.plist caches.
    toDir.setLastModified(System.currentTimeMillis());

    return new ApplicationResult(appliedActions);
  }

  public void revert(List<PatchAction> actions, final File backupDir, final File toDir, UpdaterUI ui)
    throws OperationCancelledException, IOException {
    Collections.reverse(actions);
    forEach(actions, "Reverting...", ui, false,
            new ActionsProcessor() {
              @Override
              public void forEach(PatchAction each) throws IOException {
                each.revert(toDir, backupDir);
              }
            });
  }

  private static void forEach(List<PatchAction> actions, String title, UpdaterUI ui, boolean canBeCancelled, ActionsProcessor processor)
    throws OperationCancelledException, IOException {
    ui.startProcess(title);
    if (canBeCancelled) ui.checkCancelled();

    for (int i = 0; i < actions.size(); i++) {
      PatchAction each = actions.get(i);

      ui.setStatus(each.getPath());
      if (canBeCancelled) ui.checkCancelled();

      processor.forEach(each);

      ui.setProgress((i + 1) * 100 / actions.size());
    }
  }

  public long digestFile(File toFile) throws IOException {
    if (!myIsBinary && Utils.isZipFile(toFile.getName())) {
      return Digester.digestZipFile(toFile);
    }
    else {
      return Digester.digestRegularFile(toFile);
    }
  }


  public Map<String, Long> digestFiles(File dir, List<String> ignoredFiles, UpdaterUI ui)
    throws IOException, OperationCancelledException {
    Map<String, Long> result = new LinkedHashMap<String, Long>();

    LinkedHashSet<String> paths = Utils.collectRelativePaths(dir, myIsStrict);
    for (String each : paths) {
      if (ignoredFiles.contains(each)) continue;
      ui.setStatus(each);
      ui.checkCancelled();
      result.put(each, digestFile(new File(dir, each)));
    }
    return result;
  }

  public String getOldBuild() {
    return myOldBuild;
  }

  public String getNewBuild() {
    return myNewBuild;
  }

  public boolean isStrict() {
    return myIsStrict;
  }

  public interface ActionsProcessor {
    void forEach(PatchAction each) throws IOException;
  }

  public static class ApplicationResult {
    final boolean applied;
    final List<PatchAction> appliedActions;

    public ApplicationResult(List<PatchAction> appliedActions) {
      this.applied = !appliedActions.isEmpty();
      this.appliedActions = appliedActions;
    }
  }
}
