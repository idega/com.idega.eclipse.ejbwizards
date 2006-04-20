/*
 * $Id$
 * Created on Apr 19, 2006
 *
 * Copyright (C) 2006 Idega Software hf. All Rights Reserved.
 *
 * This software is the proprietary information of Idega hf.
 * Use is subject to license terms.
 */
package com.idega.eclipse.ejbwizards;

public class MethodFilter {

	public final static int TYPE_SUFFIX = 0;
	public final static int TYPE_PREFIX = 1;
	public final static int TYPE_WHOLE = 2;

	private String filterName;
	private int type;

	public MethodFilter(String filter) {
		this(filter, TYPE_PREFIX);
	}

	public MethodFilter(String filter, int type) {
		this.filterName = filter;
		this.type = type;
	}

	public String getFilter() {
		return this.filterName;
	}

	public int getType() {
		return this.type;
	}

	/**
	 * 
	 * @param methodName
	 * @return
	 */
	public boolean filter(String methodName) {

		switch (this.type) {
			case TYPE_PREFIX:
				return methodName.startsWith(this.filterName);

			case TYPE_WHOLE:
				return methodName.equals(this.filterName);

			case TYPE_SUFFIX:
				return methodName.endsWith(this.filterName);

		}
		return false;

	}
}