package org.openlca.app.results.comparison.component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.results.comparison.display.TargetCalculationEnum;
import org.openlca.app.viewers.Viewers;
import org.openlca.app.viewers.tables.Tables;
import org.openlca.app.viewers.tables.modify.CheckBoxCellModifier;
import org.openlca.app.viewers.tables.modify.ModifySupport;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.ImpactDescriptor;

public class ImpactCategoryTable {
	private TableViewer viewer;
	List<ImpactDescriptor> categories;
	List<ImpactDescriptor> categoriesFullList;
	TargetCalculationEnum target;
	private Image icon_false;
	private Image icon_true;

	public ImpactCategoryTable(Composite body, List<ImpactDescriptor> categories, TargetCalculationEnum t) {
		target = t;
		var comp = new Composite(body, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));
		var gridData = new GridData(SWT.NONE, SWT.FILL, true, true);
		gridData.widthHint = 700;
		comp.setLayoutData(gridData);
		categories.sort((c1, c2) -> c1.name.compareTo(c2.name));
		if (target.equals(TargetCalculationEnum.PRODUCT_SYSTEM)) {
			this.categories = categories;
			icon_false = Icon.CHECK_FALSE.get();
			icon_true = Icon.CHECK_TRUE.get();
		} else {
			this.categories = new ArrayList<ImpactDescriptor>();
			this.categories.add(categories.get(0));
			icon_false = Icon.RADIO_FALSE.get();
			icon_true = Icon.RADIO_TRUE.get();
		}
		categoriesFullList = categories;
		List<CategoryVariant> l = categories.stream().map(c -> new CategoryVariant(c)).collect(Collectors.toList());
		l.get(0).isDisabled = false;
		viewer = Tables.createViewer(comp, "Impact Category", "Display"); // Create columns
		var labelProvider = new CategoryLabelProvider();
		viewer.setLabelProvider(labelProvider);
		new ModifySupport<CategoryVariant>(viewer).bind("Display", new DisplayModifier()).bind("Impact Category",
				new DisplayModifier());
		viewer.setInput(l);
		
		Viewers.sortByLabels(viewer, labelProvider, 0);
		// FIXME
		// Need to set some null data by hand, because if we don't fully scroll, we just
		// get access to the visible element of the table
		int i = 0;
		for (var tableItem : viewer.getTable().getItems()) {
			var data = (CategoryVariant) tableItem.getData();
			if (data == null)
				tableItem.setData(l.get(i++));
		}
		Tables.bindColumnWidths(viewer, 0.8, 0.19);
		tableHeaderAction();
	}

	/**
	 * Handle click on Display column, to switch between check all, or check none
	 */
	private void tableHeaderAction() {
		var column = viewer.getTable().getColumns()[1];
		if (target.equals(TargetCalculationEnum.PRODUCT_SYSTEM))
			column.setImage(icon_true);
		var wrapper = new Object() {
			boolean isCheckAll = true;
		};
		column.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (target.equals(TargetCalculationEnum.PRODUCT_SYSTEM)) {
					wrapper.isCheckAll = !wrapper.isCheckAll;
					if (!wrapper.isCheckAll) {
						column.setImage(icon_false);
						categories = new ArrayList<ImpactDescriptor>();
					} else {
						column.setImage(icon_true);
						categories = categoriesFullList;
					}
					// Update every row of the table
					for (var tableItem : viewer.getTable().getItems()) {
						var data = (CategoryVariant) tableItem.getData();
						if (data == null)
							continue;
						data.isDisabled = !wrapper.isCheckAll;
						tableItem.setData(data);
					}
					viewer.refresh();
				}
			}
		});
	}

	/**
	 * Get the selected impact categories
	 * 
	 * @return The list of selected impact categories
	 */
	public List<ImpactDescriptor> getImpactDescriptors() {
		return categories;
	}

	private class CategoryLabelProvider extends LabelProvider implements ITableLabelProvider {
		
		@Override
		public Image getColumnImage(Object obj, int col) {
			var category = (CategoryVariant) obj;
			return switch (col) {
			case 0 -> Images.get(ModelType.IMPACT_CATEGORY);
			case 1 -> category.isDisabled ? icon_false : icon_true;
			default -> null;
			};
		}

		@Override
		public String getColumnText(Object obj, int col) {
			var category = (CategoryVariant) obj;
			return switch (col) {
			case 0 -> category.category.name;
			default -> null;
			};
		}
	}

	private class DisplayModifier extends CheckBoxCellModifier<CategoryVariant> {

		@Override
		protected boolean isChecked(CategoryVariant v) {
			return !v.isDisabled;
		}

		@Override
		protected void setChecked(CategoryVariant v, boolean b) {
			if (v.isDisabled != b)
				return;
			if (target.equals(TargetCalculationEnum.PROJECT) && !v.isDisabled) {
				return;
			}
			if (b) {
				if (target.equals(TargetCalculationEnum.PROJECT)) {
					for (var tableItem : viewer.getTable().getItems()) {
						var data = (CategoryVariant) tableItem.getData();
						if (data == null)
							continue;
						data.isDisabled = true;
						tableItem.setData(data);
					}
					categories = new ArrayList<ImpactDescriptor>();
				}
				categories.add(v.category);

			} else {
				categories.remove(v.category);
			}
			v.isDisabled = !b;
			viewer.refresh();
		}
	}

	private class CategoryVariant {
		public ImpactDescriptor category;
		public boolean isDisabled;

		public CategoryVariant(ImpactDescriptor c) {
			category = c;
			isDisabled = target.equals(TargetCalculationEnum.PRODUCT_SYSTEM) ? false : true;
		}
	}
}
