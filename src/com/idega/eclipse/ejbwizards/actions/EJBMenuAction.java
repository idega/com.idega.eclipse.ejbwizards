package com.idega.eclipse.ejbwizards.actions;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

public abstract class EJBMenuAction implements IObjectActionDelegate {

	private ISelection sel;

	/**
	 * Constructor for Action1.
	 */
	public EJBMenuAction() {
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		if (this.sel != null && this.sel instanceof IStructuredSelection) {
			IStructuredSelection structured = (IStructuredSelection) this.sel;
			Object object = structured.getFirstElement();
			IJavaElement unit = null;
			IFile file = null;
			
			if (object instanceof IType) {// case of Navigator View
				unit = ((IType) object).getCompilationUnit();
			}
			else if (object instanceof IJavaElement) { // case of Navigator View
				unit = (IJavaElement) object;
			}
			else if (object instanceof IFile) {
				file = (IFile) object;
			}

			if (unit != null) {
				IJavaElement javaElement = unit;
				if (javaElement == null) {
					return;
				}

				createResource(javaElement);
			}
			else if (file != null) {
				IFile iFile = file;
				if (iFile == null) {
					return;
				}
				createResource(file);
			}
		}
	}

	private void createResource(IFile file) {
		try {
			createResource((IResource) file);
		}
		catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void createResource(IJavaElement javaElement) {
		try {
			IResource resource = javaElement.getResource();
			createResource(resource);
		}
		catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		this.sel = selection;
	}

	protected abstract void createResource(IResource resource) throws JavaModelException;
}
