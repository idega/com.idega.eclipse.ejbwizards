/*
 * Created on 6.7.2004
 *
 * Copyright (C) 2004 Idega hf. All Rights Reserved.
 *
 *  This software is the proprietary information of Idega hf.
 *  Use is subject to license terms.
 */
package com.idega.eclipse.ejbwizards.actions;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.JavaModelException;

import com.idega.eclipse.ejbwizards.IDOEntityCreator;

/**
 * @author aron
 *
 * IDOLegacyEntityAction TODO Describe this type
 */
public class IDOLegacyEntityAction extends EJBMenuAction {

	protected void createResource(IResource resource) throws JavaModelException {
		new IDOEntityCreator(resource, true);
	}
}