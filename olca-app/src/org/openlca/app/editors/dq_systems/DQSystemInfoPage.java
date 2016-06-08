package org.openlca.app.editors.dq_systems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.M;
import org.openlca.app.editors.InfoSection;
import org.openlca.app.editors.ModelPage;
import org.openlca.app.util.Controls;
import org.openlca.app.util.UI;
import org.openlca.core.model.DQIndicator;
import org.openlca.core.model.DQScore;
import org.openlca.core.model.DQSystem;

class DQSystemInfoPage extends ModelPage<DQSystem> {

	private FormToolkit toolkit;
	private ScrolledForm form;
	private Composite body;
	private Section indicatorSection;
	private Section uncertaintySection;
	private Map<Integer, Text> indicatorTexts = new HashMap<>();

	DQSystemInfoPage(DQSystemEditor editor) {
		super(editor, "DQSystemInfoPage", M.GeneralInformation);
	}

	void redraw() {
		indicatorTexts.clear();
		indicatorSection.dispose();
		if (uncertaintySection != null)
			uncertaintySection.dispose();
		createAdditionalInfo(body);
		form.reflow(true);
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		form = UI.formHeader(managedForm);
		updateFormTitle();
		toolkit = managedForm.getToolkit();
		body = UI.formBody(form, toolkit);
		InfoSection infoSection = new InfoSection(getEditor());
		infoSection.render(body, toolkit);
		createAdditionalInfo(body);
		body.setFocus();
		form.reflow(true);
	}

	@Override
	protected void updateFormTitle() {
		if (form == null)
			return;
		form.setText("#Data quality system" + ": " + getModel().getName());
	}

	private void createAdditionalInfo(Composite body) {
		Collections.sort(getModel().indicators);
		for (DQIndicator indicator : getModel().indicators) {
			Collections.sort(indicator.scores);
		}
		indicatorSection = UI.section(body, toolkit, "#Indicators & Scores");
		Composite indicatorClient = UI.sectionClient(indicatorSection, toolkit);
		createIndicatorMatrix(indicatorClient);
		if (!getModel().hasUncertainties)
			return;
		uncertaintySection = UI.section(body, toolkit, "#Uncertainties");
		Composite uncertaintyClient = UI.sectionClient(uncertaintySection, toolkit);
		createUncertaintyMatrix(uncertaintyClient);
	}

	private void createIndicatorMatrix(Composite composite) {
		UI.gridLayout(composite, getModel().getScoreCount() + 2);
		createHeader(composite);
		createAddScoreButton(composite);
		for (DQIndicator indicator : getModel().indicators) {
			Text nameText = createTextCell(composite, 1, 15);
			((GridData) nameText.getLayoutData()).verticalAlignment = SWT.TOP;
			getBinding().onString(() -> indicator, "name", nameText);
			indicatorTexts.put(indicator.position, nameText);
			for (DQScore score : indicator.scores) {
				Text descriptionText = createTextCell(composite, 8, 8);
				getBinding().onString(() -> score, "description", descriptionText);

			}
			createRemoveIndicatorButton(composite, indicator.position);
		}
		createAddIndicatorButton(composite);
		for (int i = 1; i <= getModel().getScoreCount(); i++) {
			createRemoveScoreButton(composite, i);
		}
	}

	private void createUncertaintyMatrix(Composite composite) {
		UI.gridLayout(composite, getModel().getScoreCount() + 1);
		createHeader(composite);
		for (DQIndicator indicator : getModel().indicators) {
			String name = indicator.name != null ? indicator.name : "";
			Label label = toolkit.createLabel(composite, name);
			label.setToolTipText(name);
			setGridData(label, 1, 15);
			Text indicatorText = indicatorTexts.get(indicator.position);
			indicatorText.addModifyListener((e) -> {
				label.setText(indicatorText.getText());
				label.setToolTipText(indicatorText.getText());
			});
			for (DQScore score : indicator.scores) {
				Text uncertaintyText = createTextCell(composite, 1, 8);
				getBinding().onDouble(() -> score, "uncertainty", uncertaintyText);
			}
		}
	}

	private void createHeader(Composite composite) {
		UI.formLabel(composite, "");
		for (int i = 1; i <= getModel().getScoreCount(); i++) {
			Label label = UI.formLabel(composite, Integer.toString(i));
			((GridData) label.getLayoutData()).horizontalAlignment = SWT.CENTER;
		}
	}

	private Text createTextCell(Composite composite, int heightFactor, int widthFactor) {
		Text text = toolkit.createText(composite, null, SWT.BORDER | SWT.MULTI | SWT.WRAP);
		setGridData(text, heightFactor, widthFactor);
		return text;
	}

	private void setGridData(Control control, int heightFactor, int widthFactor) {
		GC gc = new GC(control);
		try {
			gc.setFont(control.getFont());
			FontMetrics fm = gc.getFontMetrics();
			GridData gd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
			gd.heightHint = heightFactor * fm.getHeight();
			gd.widthHint = widthFactor * fm.getHeight();
			control.setLayoutData(gd);
		} finally {
			gc.dispose();
		}
	}

	private void createAddScoreButton(Composite parent) {
		Button button = toolkit.createButton(parent, "#Add score", SWT.NONE);
		Controls.onSelect(button, (e) -> {
			int newScore = getModel().getScoreCount() + 1;
			for (DQIndicator indicator : getModel().indicators) {
				DQScore score = new DQScore();
				score.position = newScore;
				score.description = indicator.name + " - score " + newScore;
				indicator.scores.add(score);
			}
			getEditor().setDirty(true);
			redraw();
		});
	}

	private void createRemoveScoreButton(Composite parent, int position) {
		Button button = toolkit.createButton(parent, "#Remove score", SWT.NONE);
		Controls.onSelect(button, (e) -> {
			for (DQIndicator indicator : getModel().indicators) {
				for (DQScore score : new ArrayList<>(indicator.scores)) {
					if (score.position < position)
						continue;
					if (score.position == position) {
						indicator.scores.remove(score);
						continue;
					}
					score.position--;
				}
			}
			getEditor().setDirty(true);
			redraw();
		});
	}

	private void createAddIndicatorButton(Composite parent) {
		Button button = toolkit.createButton(parent, "#Add indicator", SWT.NONE);
		Controls.onSelect(button, (e) -> {
			DQIndicator indicator = new DQIndicator();
			indicator.name = "Indicator " + (getModel().indicators.size() + 1);
			indicator.position = getModel().indicators.size() + 1;
			for (int i = 1; i <= getModel().getScoreCount(); i++) {
				DQScore score = new DQScore();
				score.position = i;
				score.description = indicator.name + " - score " + i;
				indicator.scores.add(score);
			}
			getModel().indicators.add(indicator);
			getEditor().setDirty(true);
			redraw();
		});
	}

	private void createRemoveIndicatorButton(Composite parent, int position) {
		Button button = toolkit.createButton(parent, "#Remove indicator", SWT.NONE);
		Controls.onSelect(button, (e) -> {
			for (DQIndicator indicator : new ArrayList<>(getModel().indicators)) {
				if (indicator.position < position)
					continue;
				if (indicator.position == position) {
					getModel().indicators.remove(indicator);
					continue;
				}
				indicator.position--;
			}
			getEditor().setDirty(true);
			redraw();
		});
	}
}
