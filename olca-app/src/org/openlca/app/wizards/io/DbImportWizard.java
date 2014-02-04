package org.openlca.app.wizards.io;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.openlca.app.Messages;
import org.openlca.app.db.Database;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.resources.ImageType;
import org.openlca.app.util.Question;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.derby.DerbyDatabase;
import org.openlca.core.database.upgrades.Upgrades;
import org.openlca.core.database.upgrades.VersionState;
import org.openlca.io.olca.DatabaseImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * Wizards for the import of data from an openLCA database to another openLCA
 * database.
 */
public class DbImportWizard extends Wizard implements IImportWizard {

	private Logger log = LoggerFactory.getLogger(this.getClass());
	private DbImportPage page;

	@Override
	public void init(IWorkbench iWorkbench,
	                 IStructuredSelection iStructuredSelection) {
		setWindowTitle(Messages.DatabaseImport);
		setDefaultPageImageDescriptor(ImageType.IMPORT_ZIP_WIZARD
				.getDescriptor());
		setNeedsProgressMonitor(true);
	}

	@Override
	public boolean performFinish() {
		if (Database.get() == null) {
			org.openlca.app.util.Error.showBox("No database activated",
					"You need to activate a target database of the import.");
			return true;
		}
		try {
			DbImportPage.ImportConfig config = page.getConfig();
			ConnectionDispatch connectionDispatch = createConnection(config);
			boolean canRun = canRun(config, connectionDispatch);
			if (!canRun) {
				connectionDispatch.close();
				return false;
			}
			ImportDispatch importDispatch = new ImportDispatch(connectionDispatch);
			getContainer().run(true, true, importDispatch);
			Navigator.refresh();
			return true;
		} catch (Exception e) {
			log.error("database import failed", e);
			return false;
		}
	}

	private boolean canRun(DbImportPage.ImportConfig config,
	                       ConnectionDispatch connectionDispatch) {
		VersionState state = connectionDispatch.getSourceState();
		if (state == VersionState.CURRENT)
			return true;
		if (state == null || state == VersionState.ERROR) {
			org.openlca.app.util.Error.showBox("Connection failed", "Could not "
					+ "get the version from the source database.");
			return false;
		}
		if (state == VersionState.NEWER) {
			org.openlca.app.util.Error.showBox("Version newer", "The version of "
					+ "the source database is newer than this version of openLCA.");
			return false;
		}
		if (config.getMode() == config.FILE_MODE)
			return true;
		return Question.ask("Update source database?", "In order to run the " +
				"import you need to update the source database. Do you want to" +
				" do this?");
	}

	private ConnectionDispatch createConnection(DbImportPage.ImportConfig config)
			throws Exception {
		ConnectionDispatch connectionDispatch = new ConnectionDispatch(config);
		PlatformUI.getWorkbench().getProgressService()
				.busyCursorWhile(connectionDispatch);
		return connectionDispatch;
	}

	@Override
	public void addPages() {
		page = new DbImportPage();
		addPage(page);
	}

	private class ImportDispatch implements IRunnableWithProgress {

		private IDatabase sourceDatabase;
		private VersionState sourceState;
		private ConnectionDispatch connectionDispatch;

		ImportDispatch(ConnectionDispatch connectionDispatch) {
			this.sourceDatabase = connectionDispatch.getSource();
			this.sourceState = connectionDispatch.getSourceState();
			this.connectionDispatch = connectionDispatch;
		}

		@Override
		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, OperationCanceledException {
			try {
				monitor.beginTask("Import database", IProgressMonitor.UNKNOWN);
				if (sourceState == VersionState.OLDER) {
					monitor.subTask("Update source database");
					Upgrades.runUpgrades(sourceDatabase);
				}
				monitor.subTask("Import data...");
				DatabaseImport dbImport = new DatabaseImport(sourceDatabase,
						Database.get());
				log.trace("run data import");
				dbImport.run();
				monitor.subTask("Close source database...");
				connectionDispatch.close();
				monitor.done();
			} catch (Exception e) {
				throw new InvocationTargetException(e);
			}
		}
	}

	/**
	 * Creates the initial resources and opens a database connection to the
	 * source database of the import.
	 */
	private class ConnectionDispatch implements IRunnableWithProgress {

		private DbImportPage.ImportConfig config;
		private File tempDbFolder;
		private IDatabase source;

		ConnectionDispatch(DbImportPage.ImportConfig config) {
			this.config = config;
		}

		public IDatabase getSource() {
			return source;
		}

		public VersionState getSourceState() {
			return Upgrades.checkVersion(source);
		}

		@Override
		public void run(IProgressMonitor monitor) throws
				InvocationTargetException, InterruptedException {
			log.trace("connect to source database");
			try {
				if (config.getMode() == config.FILE_MODE)
					source = connectToFolder();
				else
					source = config.getDatabaseConfiguration().createInstance();
			} catch (Exception e) {
				log.error("Failed to connect to source database", e);
				throw new InvocationTargetException(e);
			}
		}

		private IDatabase connectToFolder() {
			File zipFile = config.getFile();
			File tempDir = new File(System.getProperty("java.io.tmpdir"));
			tempDbFolder = new File(tempDir, UUID.randomUUID().toString());
			tempDbFolder.mkdirs();
			log.trace("unpack zolca file to {}", tempDbFolder);
			ZipUtil.unpack(zipFile, tempDbFolder);
			return new DerbyDatabase(tempDbFolder);
		}


		void close() throws Exception {
			log.trace("close source database");
			source.close();
			if (tempDbFolder != null) {
				log.trace("delete temporary db-folder {}", tempDbFolder);
				FileUtils.deleteDirectory(tempDbFolder);
			}
		}
	}

}