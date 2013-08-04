package org.openlca.core.editors.result;

import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.Messages;
import org.openlca.app.util.UI;
import org.openlca.app.util.UIFactory;
import org.openlca.core.editors.ModelEditor;
import org.openlca.core.editors.ModelEditorPage;

/**
 * Info page for the analyze editor. Displays the product system and LCIA
 * information
 * 
 * @author Sebastian Greve
 * 
 */
public class ResultInfoPage extends ModelEditorPage {

	private ResultInfo resultInfo;
	private FormToolkit toolkit;

	public ResultInfoPage(ModelEditor editor, ResultInfo info) {
		super(editor, "org.openlca.core.editors.analyze",
				Messages.GeneralInformation);
		this.resultInfo = info;
	}

	@Override
	protected void createContents(Composite body, FormToolkit toolkit) {
		this.toolkit = toolkit;
		GridLayout layout = (GridLayout) UIFactory
				.createGridLayout(2, false, 5);
		layout.marginTop = 25;
		body.setLayout(layout);
		createTexts(body);
	}

	private void createTexts(Composite body) {
		if (resultInfo == null)
			return;
		createText(body, Messages.ProductSystem,
				resultInfo.getProductSystem());
		createText(body, Messages.TargetAmount,
				resultInfo.getProductFlow());
		createText(body, Messages.ImpactMethod,
				resultInfo.getImpactMethod());
		createText(body, Messages.NormalizationWeightingSet,
				resultInfo.getNwSet());
		createText(body, Messages.CalculationMethod,
				Messages.MatrixMethod);
	}

	private void createText(Composite body, String label, String value) {
		if (value == null)
			return;
		Text text = UI.formText(body, toolkit, label);
		text.setText(value);
		text.setEditable(false);
	}

	@Override
	protected String getFormTitle() {
		String sys = resultInfo != null ? resultInfo.getProductSystem() : "";
		return NLS.bind(Messages.ResultsOf, sys);
	}

	@Override
	protected void initListeners() {
	}

	@Override
	protected void setData() {
	}

}
