/*******************************************************************************
 * Copyright (c) 2007 - 2010 GreenDeltaTC. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Mozilla
 * Public License v1.1 which accompanies this distribution, and is available at
 * http://www.openlca.org/uploads/media/MPL-1.1.html
 * 
 * Contributors: GreenDeltaTC - initial API and implementation
 * www.greendeltatc.com tel.: +49 30 4849 6030 mail: gdtc@greendeltatc.com
 ******************************************************************************/
package org.openlca.app.editors.graphical.model;

import java.util.List;

import org.eclipse.draw2d.GridData;
import org.eclipse.draw2d.IFigure;
import org.eclipse.swt.SWT;

class InputOutputPart extends AppAbstractEditPart<InputOutputNode> {

	@Override
	protected IFigure createFigure() {
		return new InputOutputFigure();
	}

	@Override
	protected void createEditPolicies() {

	}

	@Override
	public ProcessPart getParent() {
		return (ProcessPart) super.getParent();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<ExchangePart> getChildren() {
		return super.getChildren();
	}

	@Override
	public boolean isSelectable() {
		return false;
	}

	@Override
	protected void refreshVisuals() {
		getFigure().getParent().setConstraint(getFigure(),
				new GridData(SWT.FILL, SWT.FILL, true, true));
	}

}
