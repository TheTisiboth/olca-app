package org.openlca.app.editors.processes.exchanges;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.FormDialog;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.Messages;
import org.openlca.app.db.Database;
import org.openlca.app.util.UI;
import org.openlca.app.util.Viewers;
import org.openlca.core.database.CostCategoryDao;
import org.openlca.core.model.CostCategory;
import org.openlca.core.model.Exchange;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CostDialog extends FormDialog {

	private Logger log = LoggerFactory.getLogger(getClass());

	private Exchange exchange;
	private ComboViewer categoryCombo;
	private Text priceText;
	private Text pricePerUnitText;

	public static int open(Exchange exchange) {
		if (exchange == null)
			return CANCEL;
		CostDialog d = new CostDialog(exchange);
		return d.open();
	}

	private CostDialog(Exchange exchange) {
		super(UI.shell());
		this.exchange = exchange;
	}

	@Override
	protected void createFormContent(IManagedForm mform) {
		FormToolkit tk = mform.getToolkit();
		UI.formHeader(mform, "#Price");
		Composite body = UI.formBody(mform.getForm(), tk);
		UI.gridLayout(body, 3);
		createCategoryRow(body, tk);
		createCostsRow(body, tk);
		createCostsPerUnitRow(body, tk);
	}

	private void createCategoryRow(Composite body, FormToolkit tk) {
		Combo widget = UI.formCombo(body, tk, Messages.CostCategory);
		categoryCombo = new ComboViewer(widget);
		categoryCombo.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object obj) {
				if (!(obj instanceof CostCategory))
					return null;
				return ((CostCategory) obj).getName();
			}
		});
		categoryCombo.setContentProvider(ArrayContentProvider.getInstance());
		CostCategoryDao dao = new CostCategoryDao(Database.get());
		List<CostCategory> all = dao.getAll();
		Collections.sort(all,
				(c1, c2) -> Strings.compare(c1.getName(), c2.getName()));
		categoryCombo.setInput(all);
		if (exchange.costCategory != null)
			categoryCombo.setSelection(new StructuredSelection(exchange.costCategory));
		categoryCombo.addSelectionChangedListener(
				e -> exchange.costCategory = Viewers.getFirst(e.getSelection()));
		UI.formLabel(body, tk, "");
	}

	private void createCostsRow(Composite body, FormToolkit tk) {
		priceText = UI.formText(body, tk, "#Costs");
		UI.formLabel(body, tk, "USD");
		if (exchange.costValue != null)
			priceText.setText(Double.toString(exchange.costValue));
		priceText.addModifyListener(e -> {
			try {
				double price = Double.parseDouble(priceText.getText());
				exchange.costValue = price;
				double perUnit = price / exchange.getAmountValue();
				pricePerUnitText.setText(Double.toString(perUnit));
			} catch (Exception ex) {
				log.trace("not a number (exchange price)", ex);
			}
		});
	}

	private void createCostsPerUnitRow(Composite body, FormToolkit tk) {
		pricePerUnitText = UI.formText(body, tk, "#Costs per unit");
		pricePerUnitText.setEnabled(false);
		String unit = exchange.getUnit().getName();
		UI.formLabel(body, tk, "USD / " + unit);
		if (exchange.costValue != null) {
			double perUnit = exchange.costValue / exchange.getAmountValue();
			pricePerUnitText.setText(Double.toString(perUnit));
		}
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID,
				IDialogConstants.OK_LABEL, true);
	}

	@Override
	protected Point getInitialSize() {
		int width = 500;
		int height = 300;
		Rectangle shellBounds = getShell().getDisplay().getBounds();
		int shellWidth = shellBounds.x;
		int shellHeight = shellBounds.y;
		if (shellWidth > 0 && shellWidth < width)
			width = shellWidth;
		if (shellHeight > 0 && shellHeight < height)
			height = shellHeight;
		return new Point(width, height);
	}

	@Override
	protected Point getInitialLocation(Point initialSize) {
		Point loc = super.getInitialLocation(initialSize);
		int marginTop = (getParentShell().getSize().y - initialSize.y) / 3;
		if (marginTop < 0)
			marginTop = 0;
		return new Point(loc.x, loc.y + marginTop);
	}

}