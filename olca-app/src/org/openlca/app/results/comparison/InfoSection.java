package org.openlca.app.results.comparison;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.openlca.app.App;
import org.openlca.app.M;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.results.comparison.display.TargetCalculationEnum;
import org.openlca.app.util.Colors;
import org.openlca.app.util.Controls;
import org.openlca.app.util.Labels;
import org.openlca.app.util.UI;
import org.openlca.core.model.CalculationSetup;
import org.openlca.core.model.CategorizedEntity;
import org.openlca.core.model.Project;
import org.openlca.core.model.descriptors.CategorizedDescriptor;

public class InfoSection {

	static void create(Composite body, FormToolkit tk, CalculationSetup setup, TargetCalculationEnum target) {
		if (setup == null || setup.productSystem() == null)
			return;
		Composite comp = UI.formSection(body, tk, M.GeneralInformation);
////		Label description = new Label(comp, SWT.NONE);
////		description.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		String descriptionText = "";
		if (setup.impactMethod() == null) {
			descriptionText += "Please choose an impact method, so we are able to display some information about the selected processes. In order to do so, you have to restart the calculation.";
		}
		if (target.equals(TargetCalculationEnum.PRODUCT_SYSTEM))
			descriptionText = "This diagram display the environmental impact of a product system or a process for each chosen impact categories."
					+ " One horizontal bar contains all the processes contributions from the product system or process that we chose, for one impact category."
					+ " The same processes contributions are linked together, from 2 bar which are next to each other. "
					+ "The processes contributions are sorted by ascending order, from left to right.\n"
					+ "In the setting section, you can select the impact categories that you want to analyze."
					+ " Initialy, they are all selected, with the alphabetical order. If you want to select/unselect impact categories,"
					+ " the order in which you select them will change the order in which the contributions are displayed."
					+ " You can change the way the processes contributions are colored by "
					+ "(either by processes, by location or by process category). "
					+ "It means that if you select Location, then 2 processes which have the same location will have the same color. "
					+ "You can also choose to highlight a specific process category, "
					+ "and change the color that will be displayed for the chosen process category. "
					+ "You can finally set up a cut-off limit, which indicate how much processes you want to be hidden. "
					+ "The higher the number is, the less processes are displayed.";
		UI.formLabel(comp, descriptionText);
//		text(comp, tk, "Description", descriptionText);

	}

	static void create(Composite body, FormToolkit tk, Project project) {
		if (project == null)
			return;
		Composite comp = UI.formSection(body, tk, M.GeneralInformation);
		String descriptionText = "This diagram display the environmental impact for a chosen impact category for each product system from the project."
				+ " One horizontal bar contains all the processes contributions from a product system from the project, for the chosen impact category."
				+ " The same processes contributions are linked together, from 2 bar which are next to each other. "
				+ "The processes contributions are sorted by ascending order, from left to right.\n"
				+ "In the setting section, you can select one impact category at a time. that you want to analyze."
				+ " You can change the way the processes contributions are colored by "
				+ "(either by processes, by location or by process category). "
				+ "It means that if you select Location, then 2 processes which have the same location will have the same color. "
				+ "You can also choose to highlight a specific process category, "
				+ "and change the color that will be displayed for the chosen process category. "
				+ "You can finally set up a cut-off limit, which indicate how much processes you want to be hidden. "
				+ "The higher the number is, the less processes are displayed.";
		UI.formLabel(comp, descriptionText);
//		text(comp, tk, "Description", descriptionText);

	}

	static void text(Composite comp, FormToolkit tk, String label, String val) {
		Text text = UI.formText(comp, tk, label);
		if (val != null) {
			text.setText(val);
		}
		text.setEditable(false);
	}

	public static void link(Composite comp, String label, Object entity) {
		new Label(comp, SWT.NONE).setText(label);
		ImageHyperlink link = new ImageHyperlink(comp, SWT.TOP);
		link.setForeground(Colors.linkBlue());
		if (entity instanceof CategorizedDescriptor) {
			CategorizedDescriptor d = (CategorizedDescriptor) entity;
			link.setText(Labels.name(d));
			link.setImage(Images.get(d));
			Controls.onClick(link, e -> App.open(d));
		} else if (entity instanceof CategorizedEntity) {
			CategorizedEntity ce = (CategorizedEntity) entity;
			link.setText(Labels.name(ce));
			link.setImage(Images.get(ce));
			Controls.onClick(link, e -> App.open(ce));
		}
	}
}