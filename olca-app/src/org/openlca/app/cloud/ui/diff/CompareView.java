package org.openlca.app.cloud.ui.diff;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.openlca.app.App;
import org.openlca.app.M;
import org.openlca.app.cloud.CloudUtil;
import org.openlca.app.cloud.JsonLoader;
import org.openlca.app.cloud.index.Diff;
import org.openlca.app.cloud.index.DiffIndex;
import org.openlca.app.cloud.index.DiffType;
import org.openlca.app.cloud.ui.FetchNotifierMonitor;
import org.openlca.app.db.Database;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.navigation.elements.CategoryElement;
import org.openlca.app.navigation.elements.DatabaseElement;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.navigation.elements.ModelElement;
import org.openlca.app.navigation.elements.ModelTypeElement;
import org.openlca.app.util.Actions;
import org.openlca.app.util.MsgBox;
import org.openlca.app.util.UI;
import org.openlca.app.viewers.Viewers;
import org.openlca.cloud.api.RepositoryClient;
import org.openlca.cloud.model.data.Commit;
import org.openlca.cloud.model.data.Dataset;
import org.openlca.cloud.model.data.FetchRequestData;
import org.openlca.cloud.model.data.FileReference;
import org.openlca.cloud.util.WebRequests.WebRequestException;
import org.openlca.core.model.Category;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompareView extends ViewPart {

	public final static String ID = "views.cloud.compare";
	private final static Logger log = LoggerFactory.getLogger(CompareView.class);
	private JsonLoader jsonLoader;
	private CompareDiffViewer viewer;
	private DiffNode input;
	private List<INavigationElement<?>> currentSelection;
	private Commit commit;

	public static void clear() {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		if (page == null)
			return;
		for (IViewReference viewRef : page.getViewReferences()) {
			if (!ID.equals(viewRef.getId()))
				continue;
			CompareView view = (CompareView) viewRef.getView(false);
			if (view == null)
				return;
			view.commit = null;
			view.currentSelection = null;
			view.input = null;
			view.viewer.setInput(new ArrayList<>());
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite body = new Composite(parent, SWT.NONE);
		UI.gridLayout(body, 1, 0, 0);
		RepositoryClient client = Database.getRepositoryClient();
		jsonLoader = CloudUtil.getJsonLoader(client);
		viewer = new CompareDiffViewer(body, jsonLoader);
		Actions.bind(viewer.getViewer(), new OverwriteAction());
	}

	/**
	 * @param commitIsAhead
	 *            true means that we are comparing with a commit that is not yet
	 *            fetched (relevant in compare view ui), false means we are
	 *            comparing with a commit that is already fetched
	 */
	public static void update(List<INavigationElement<?>> elements, Commit commit, boolean commitIsAhead) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (page == null)
				return;
			CompareView view = (CompareView) page.showView(ID);
			view.viewer.setLabelProvider(commitIsAhead ? ActionType.COMPARE_AHEAD : ActionType.COMPARE_BEHIND);
			view.update(elements, commit);
		} catch (PartInitException e) {
			log.error("Error compare view", e);
		}
	}

	private void update(List<INavigationElement<?>> elements, Commit commit) {
		if (!Database.isConnected()) {
			viewer.setInput(new ArrayList<>());
			return;
		}
		this.currentSelection = elements;
		this.commit = commit;
		if (jsonLoader == null) {
			jsonLoader = CloudUtil.getJsonLoader(Database.getRepositoryClient());
		} else {
			jsonLoader.setClient(Database.getRepositoryClient());
		}
		jsonLoader.setCommitId(commit != null ? commit.id : null);
		App.runWithProgress("Comparing data sets", () -> loadInput(elements));
		viewer.setInput(input != null ? Collections.singleton(input) : new ArrayList<>());
	}

	private void loadInput(List<INavigationElement<?>> elements) {
		try {
			RepositoryClient client = Database.getRepositoryClient();
			if (client == null)
				input = null;
			DiffIndex index = Database.getDiffIndex();
			Set<FetchRequestData> descriptors = client.sync(commit != null ? commit.id : null);
			List<DiffResult> differences = createDifferences(descriptors, elements);
			input = new DiffNodeBuilder(client.getConfig().database, index, ActionType.COMPARE_AHEAD).build(differences);
		} catch (Exception e) {
			log.error("Error loading remote data", e);
			input = null;
		}
	}

	private boolean isContainedIn(Dataset dataset, List<INavigationElement<?>> elements) {
		if (elements == null || elements.isEmpty())
			return true;
		for (INavigationElement<?> element : elements)
			if (isContainedIn(dataset, element))
				return true;
		return false;
	}

	private boolean isContainedIn(Dataset dataset, INavigationElement<?> element) {
		if (element instanceof DatabaseElement)
			return true;
		if (element instanceof ModelTypeElement) {
			ModelType type = ((ModelTypeElement) element).getContent();
			if (dataset.type == ModelType.CATEGORY && type == dataset.categoryType)
				return true;
			if (type == dataset.type)
				return true;
		}
		if (element instanceof CategoryElement) {
			Category category = ((CategoryElement) element).getContent();
			if (dataset.type == ModelType.CATEGORY)
				if (category.refId.equals(dataset.refId))
					return true;
			if (dataset.type == category.modelType)
				if (isContainedIn(category, dataset.categories))
					return true;
		}
		if (element instanceof ModelElement) {
			CategorizedDescriptor descriptor = ((ModelElement) element).getContent();
			if (descriptor.refId.equals(dataset.refId))
				return true;
		}
		for (INavigationElement<?> child : element.getChildren())
			if (isContainedIn(dataset, child))
				return true;
		return false;
	}

	private boolean isContainedIn(Category category, List<String> categories) {
		List<String> categoryPath = new ArrayList<>();
		while (category != null) {
			categoryPath.add(0, category.name);
			category = category.category;
		}
		if (categoryPath.size() > categories.size())
			return false;
		for (int i = 0; i < categoryPath.size(); i++)
			if (!categoryPath.get(i).equals(categories.get(i)))
				return false;
		return true;
	}

	private List<DiffResult> createDifferences(Set<FetchRequestData> remotes, List<INavigationElement<?>> elements) {
		DiffIndex index = Database.getDiffIndex();
		List<DiffResult> differences = new ArrayList<>();
		Set<String> checked = new HashSet<>();
		for (FetchRequestData identifier : remotes) {
			Diff local = index.get(identifier);
			if (local != null && !isContainedIn(local.getDataset(), elements))
				continue;
			if (local == null && !isContainedIn(identifier, elements))
				continue;
			if (local == null && identifier.isDeleted()) {
				checked.add(identifier.toId());
				continue;
			}
			if (local != null && local.type != DiffType.DELETED && !identifier.isDeleted()
					&& local.getDataset().type != ModelType.CATEGORY
					&& local.getDataset().equals(identifier)) {
				checked.add(identifier.toId());
				continue;
			}
			differences.add(new DiffResult(local, identifier));
			checked.add(identifier.toId());
		}
		for (Diff diff : index.getAll()) {
			if (checked.contains(diff.getDataset().toId()))
				continue;
			if (!isContainedIn(diff.getDataset(), elements))
				continue;
			differences.add(new DiffResult(diff, null));
		}
		return differences;
	}

	@Override
	public void setFocus() {

	}

	private class OverwriteAction extends Action {

		private Exception error;

		private OverwriteAction() {
			setText("Overwrite local changes");
		}

		@Override
		public void run() {
			List<DiffNode> selected = Viewers.getAllSelected(viewer.getViewer());
			Set<FileReference> remotes = collectDatasets(selected);
			RepositoryClient client = Database.getRepositoryClient();
			ProgressMonitorDialog dialog = new ProgressMonitorDialog(UI.shell());
			String commitId = commit != null ? commit.id : null;
			try {
				dialog.run(true, false, new IRunnableWithProgress() {

					@Override
					public void run(IProgressMonitor m) throws InvocationTargetException, InterruptedException {
						try {
							FetchNotifierMonitor monitor = new FetchNotifierMonitor(m, M.DownloadingData);
							client.download(remotes, commitId, monitor);
						} catch (WebRequestException e) {
							throw new InvocationTargetException(e, e.getMessage());
						}
					}
				});
			} catch (Exception e) {
				error = e;
			} finally {
				Navigator.refresh();
			}
			if (error != null)
				MsgBox.error("Error during download", error.getMessage());
			else {
				update(currentSelection, commit);
			}
		}

		private Set<FileReference> collectDatasets(List<DiffNode> nodes) {
			Set<FileReference> remotes = new HashSet<>();
			for (DiffNode node : nodes) {
				if (node.getContent().remote != null) {
					remotes.add(node.getContent().getDataset().asFileReference());
				}
				remotes.addAll(collectDatasets(node.children));
			}
			return remotes;
		}

	}

}
