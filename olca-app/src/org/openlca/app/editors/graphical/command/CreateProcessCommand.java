/*******************************************************************************
 * Copyright (c) 2007 - 2010 GreenDeltaTC. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Mozilla
 * Public License v1.1 which accompanies this distribution, and is available at
 * http://www.openlca.org/uploads/media/MPL-1.1.html
 * 
 * Contributors: GreenDeltaTC - initial API and implementation
 * www.greendeltatc.com tel.: +49 30 4849 6030 mail: gdtc@greendeltatc.com
 ******************************************************************************/
package org.openlca.app.editors.graphical.command;

import org.eclipse.gef.commands.Command;
import org.openlca.app.Messages;
import org.openlca.app.editors.graphical.model.ProcessNode;
import org.openlca.app.editors.graphical.model.ProductSystemNode;
import org.openlca.core.model.descriptors.ProcessDescriptor;

public class CreateProcessCommand extends Command {

	private ProductSystemNode model;
	private ProcessDescriptor process;

	CreateProcessCommand() {

	}

	@Override
	public boolean canExecute() {
		if (model == null)
			return false;
		return !model.getProductSystem().getProcesses()
				.contains(process.getId());
	}

	@Override
	public boolean canUndo() {
		if (model == null)
			return false;
		return model.getProductSystem().getProcesses()
				.contains(process.getId());
	}

	@Override
	public void execute() {
		model.getProductSystem().getProcesses().add(process.getId());
		model.add(new ProcessNode(process));
		if (model.getEditor().getOutline() != null)
			model.getEditor().getOutline().refresh();
	}

	@Override
	public String getLabel() {
		return Messages.Systems_ProcessCreateCommand_Text;
	}

	@Override
	public void redo() {
		execute();
	}

	void setModel(ProductSystemNode model) {
		this.model = model;
	}

	void setProcess(ProcessDescriptor process) {
		this.process = process;
	}

	@Override
	public void undo() {
		model.getProductSystem().getProcesses().remove(process.getId());
		model.remove(model.getProcessNode(process.getId()));
		if (model.getEditor().getOutline() != null)
			model.getEditor().getOutline().refresh();
	}
}
