/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genady Beryozkin <eclipse@genady.org> - [hovering] tooltip for constant string does not show constant value - https://bugs.eclipse.org/bugs/show_bug.cgi?id=85382
 *******************************************************************************/
package ext.org.eclipse.jdt.internal.ui.text.java.hover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;

import org.osgi.framework.Bundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput;
import org.eclipse.jface.internal.text.html.BrowserInput;
import org.eclipse.jface.internal.text.html.HTMLPrinter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension4;
import org.eclipse.jface.text.IInputChangedListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.editors.text.EditorsUI;

import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.OpenAttachedJavadocAction;

import patch.org.eclipse.jdt.internal.ui.JavaPlugin;



import ext.org.eclipse.jdt.internal.corext.javadoc.JavaDocLocations;
import ext.org.eclipse.jdt.internal.corext.util.JdtFlags;
import ext.org.eclipse.jdt.internal.corext.util.Messages;
import ext.org.eclipse.jdt.internal.ui.JavaPluginImages;
import ext.org.eclipse.jdt.internal.ui.actions.OpenBrowserUtil;
import ext.org.eclipse.jdt.internal.ui.actions.SimpleSelectionProvider;
import ext.org.eclipse.jdt.internal.ui.infoviews.JavadocView;
import ext.org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import ext.org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2;
import ext.org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import ext.org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks;


/**
 * Provides Javadoc as hover info for Java elements.
 *
 * @since 2.1
 */
public class JavadocHover extends AbstractJavaEditorTextHover {

	/**
	 * Action to go back to the previous input in the hover control.
	 *
	 * @since 3.4
	 */
	private static final class BackAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public BackAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_back);
			ISharedImages images= PlatformUI.getWorkbench().getSharedImages();
			setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_BACK));
			setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_BACK_DISABLED));

			update();
		}

		@Override
		public void run() {
			BrowserInformationControlInput previous= (BrowserInformationControlInput) fInfoControl.getInput().getPrevious();
			if (previous != null) {
				fInfoControl.setInput(previous);
			}
		}

		public void update() {
			BrowserInformationControlInput current= fInfoControl.getInput();

			if (current != null && current.getPrevious() != null) {
				BrowserInput previous= current.getPrevious();
				setToolTipText(Messages.format(JavaHoverMessages.JavadocHover_back_toElement_toolTip, BasicElementLabels.getJavaElementName(previous.getInputName())));
				setEnabled(true);
			} else {
				setToolTipText(JavaHoverMessages.JavadocHover_back);
				setEnabled(false);
			}
		}
	}

	/**
	 * Action to go forward to the next input in the hover control.
	 *
	 * @since 3.4
	 */
	private static final class ForwardAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public ForwardAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_forward);
			ISharedImages images= PlatformUI.getWorkbench().getSharedImages();
			setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD));
			setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD_DISABLED));

			update();
		}

		@Override
		public void run() {
			BrowserInformationControlInput next= (BrowserInformationControlInput) fInfoControl.getInput().getNext();
			if (next != null) {
				fInfoControl.setInput(next);
			}
		}

		public void update() {
			BrowserInformationControlInput current= fInfoControl.getInput();

			if (current != null && current.getNext() != null) {
				setToolTipText(Messages.format(JavaHoverMessages.JavadocHover_forward_toElement_toolTip, BasicElementLabels.getJavaElementName(current.getNext().getInputName())));
				setEnabled(true);
			} else {
				setToolTipText(JavaHoverMessages.JavadocHover_forward_toolTip);
				setEnabled(false);
			}
		}
	}

	/**
	 * Action that shows the current hover contents in the Javadoc view.
	 *
	 * @since 3.4
	 */
	private static final class ShowInJavadocViewAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public ShowInJavadocViewAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_showInJavadoc);
			setImageDescriptor(JavaPluginImages.DESC_OBJS_JAVADOCTAG); //TODO: better image
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			JavadocBrowserInformationControlInput infoInput= (JavadocBrowserInformationControlInput) fInfoControl.getInput(); //TODO: check cast
			fInfoControl.notifyDelayedInputChange(null);
			fInfoControl.dispose(); //FIXME: should have protocol to hide, rather than dispose
			try {
				JavadocView view= (JavadocView) JavaPlugin.getActivePage().showView(JavaUI.ID_JAVADOC_VIEW);
				view.setInput(infoInput);
			} catch (PartInitException e) {
				JavaPlugin.log(e);
			}
		}
	}

	/**
	 * Action that opens the current hover input element.
	 *
	 * @since 3.4
	 */
	private static final class OpenDeclarationAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public OpenDeclarationAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_openDeclaration);
			JavaPluginImages.setLocalImageDescriptors(this, "goto_input.gif"); //$NON-NLS-1$ //TODO: better images
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			JavadocBrowserInformationControlInput infoInput= (JavadocBrowserInformationControlInput) fInfoControl.getInput(); //TODO: check cast
			fInfoControl.notifyDelayedInputChange(null);
			fInfoControl.dispose(); //FIXME: should have protocol to hide, rather than dispose

			try {
				//FIXME: add hover location to editor navigation history?
				JavaUI.openInEditor(infoInput.getElement());
			} catch (PartInitException e) {
				JavaPlugin.log(e);
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
	}


	/**
	 * Presenter control creator.
	 *
	 * @since 3.3
	 */
	public static final class PresenterControlCreator extends AbstractReusableInformationControlCreator {

		private IWorkbenchSite fSite;

		/**
		 * Creates a new PresenterControlCreator.
		 * 
		 * @param site the site or <code>null</code> if none
		 * @since 3.6
		 */
		public PresenterControlCreator(IWorkbenchSite site) {
			fSite= site;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractReusableInformationControlCreator#doCreateInformationControl(org.eclipse.swt.widgets.Shell)
		 */
		@Override
		public IInformationControl doCreateInformationControl(Shell parent) {
			if (BrowserInformationControl.isAvailable(parent)) {
				ToolBarManager tbm= new ToolBarManager(SWT.FLAT);
				String font= PreferenceConstants.APPEARANCE_JAVADOC_FONT;
				BrowserInformationControl iControl= new BrowserInformationControl(parent, font, tbm);

				final BackAction backAction= new BackAction(iControl);
				backAction.setEnabled(false);
				tbm.add(backAction);
				final ForwardAction forwardAction= new ForwardAction(iControl);
				tbm.add(forwardAction);
				forwardAction.setEnabled(false);

				final ShowInJavadocViewAction showInJavadocViewAction= new ShowInJavadocViewAction(iControl);
				tbm.add(showInJavadocViewAction);
				final OpenDeclarationAction openDeclarationAction= new OpenDeclarationAction(iControl);
				tbm.add(openDeclarationAction);

				final SimpleSelectionProvider selectionProvider= new SimpleSelectionProvider();
				if (fSite != null) {
					OpenAttachedJavadocAction openAttachedJavadocAction= new OpenAttachedJavadocAction(fSite);
					openAttachedJavadocAction.setSpecialSelectionProvider(selectionProvider);
					openAttachedJavadocAction.setImageDescriptor(JavaPluginImages.DESC_ELCL_OPEN_BROWSER);
					openAttachedJavadocAction.setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_OPEN_BROWSER);
					selectionProvider.addSelectionChangedListener(openAttachedJavadocAction);
					selectionProvider.setSelection(new StructuredSelection());
					tbm.add(openAttachedJavadocAction);
				}

				IInputChangedListener inputChangeListener= new IInputChangedListener() {
					public void inputChanged(Object newInput) {
						backAction.update();
						forwardAction.update();
						if (newInput == null) {
							selectionProvider.setSelection(new StructuredSelection());
						} else if (newInput instanceof BrowserInformationControlInput) {
							BrowserInformationControlInput input= (BrowserInformationControlInput) newInput;
							Object inputElement= input.getInputElement();
							selectionProvider.setSelection(new StructuredSelection(inputElement));
							boolean isJavaElementInput= inputElement instanceof IJavaElement;
							showInJavadocViewAction.setEnabled(isJavaElementInput);
							openDeclarationAction.setEnabled(isJavaElementInput);
						}
					}
				};
				iControl.addInputChangeListener(inputChangeListener);

				tbm.update(true);

				addLinkListener(iControl);
				return iControl;

			} else {
				return new DefaultInformationControl(parent, true);
			}
		}
	}


	/**
	 * Hover control creator.
	 *
	 * @since 3.3
	 */
	public static final class HoverControlCreator extends AbstractReusableInformationControlCreator {
		/**
		 * The information presenter control creator.
		 * @since 3.4
		 */
		private final IInformationControlCreator fInformationPresenterControlCreator;
		/**
		 * <code>true</code> to use the additional info affordance, <code>false</code> to use the hover affordance.
		 */
		private final boolean fAdditionalInfoAffordance;

		/**
		 * @param informationPresenterControlCreator control creator for enriched hover
		 * @since 3.4
		 */
		public HoverControlCreator(IInformationControlCreator informationPresenterControlCreator) {
			this(informationPresenterControlCreator, false);
		}

		/**
		 * @param informationPresenterControlCreator control creator for enriched hover
		 * @param additionalInfoAffordance <code>true</code> to use the additional info affordance, <code>false</code> to use the hover affordance
		 * @since 3.4
		 */
		public HoverControlCreator(IInformationControlCreator informationPresenterControlCreator, boolean additionalInfoAffordance) {
			fInformationPresenterControlCreator= informationPresenterControlCreator;
			fAdditionalInfoAffordance= additionalInfoAffordance;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractReusableInformationControlCreator#doCreateInformationControl(org.eclipse.swt.widgets.Shell)
		 */
		@Override
		public IInformationControl doCreateInformationControl(Shell parent) {
			String tooltipAffordanceString= fAdditionalInfoAffordance ? JavaPlugin.getAdditionalInfoAffordanceString() : EditorsUI.getTooltipAffordanceString();
			if (BrowserInformationControl.isAvailable(parent)) {
				String font= PreferenceConstants.APPEARANCE_JAVADOC_FONT;
				BrowserInformationControl iControl= new BrowserInformationControl(parent, font, tooltipAffordanceString) {
					/*
					 * @see org.eclipse.jface.text.IInformationControlExtension5#getInformationPresenterControlCreator()
					 */
					@Override
					public IInformationControlCreator getInformationPresenterControlCreator() {
						return fInformationPresenterControlCreator;
					}
				};
				addLinkListener(iControl);
				return iControl;
			} else {
				return new DefaultInformationControl(parent, tooltipAffordanceString);
			}
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractReusableInformationControlCreator#canReuse(org.eclipse.jface.text.IInformationControl)
		 */
		@Override
		public boolean canReuse(IInformationControl control) {
			if (!super.canReuse(control))
				return false;

			if (control instanceof IInformationControlExtension4) {
				String tooltipAffordanceString= fAdditionalInfoAffordance ? JavaPlugin.getAdditionalInfoAffordanceString() : EditorsUI.getTooltipAffordanceString();
				((IInformationControlExtension4)control).setStatusText(tooltipAffordanceString);
			}

			return true;
		}
	}

	private static final long LABEL_FLAGS=  JavaElementLabels.ALL_FULLY_QUALIFIED
		| JavaElementLabels.M_PRE_RETURNTYPE | JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_PARAMETER_NAMES | JavaElementLabels.M_EXCEPTIONS
		| JavaElementLabels.F_PRE_TYPE_SIGNATURE | JavaElementLabels.M_PRE_TYPE_PARAMETERS | JavaElementLabels.T_TYPE_PARAMETERS
		| JavaElementLabels.USE_RESOLVED;
	private static final long LOCAL_VARIABLE_FLAGS= LABEL_FLAGS & ~JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.F_POST_QUALIFIED;
	private static final long TYPE_PARAMETER_FLAGS= LABEL_FLAGS | JavaElementLabels.TP_POST_QUALIFIED;

	/**
	 * The style sheet (css).
	 * @since 3.4
	 */
	private static String fgStyleSheet;

	/**
	 * The hover control creator.
	 *
	 * @since 3.2
	 */
	private IInformationControlCreator fHoverControlCreator;
	/**
	 * The presentation control creator.
	 *
	 * @since 3.2
	 */
	private IInformationControlCreator fPresenterControlCreator;

	/*
	 * @see org.eclipse.jface.text.information.IInformationProviderExtension2#getInformationPresenterControlCreator()
	 * @since 3.1
	 */
	@Override
	public IInformationControlCreator getInformationPresenterControlCreator() {
		if (fPresenterControlCreator == null)
			fPresenterControlCreator= new PresenterControlCreator(getSite());
		return fPresenterControlCreator;
	}

	private IWorkbenchSite getSite() {
		IEditorPart editor= getEditor();
		if (editor == null) {
			IWorkbenchPage page= JavaPlugin.getActivePage();
			if (page != null)
				editor= page.getActiveEditor();
		}
		if (editor != null)
			return editor.getSite();

		return null;
	}

	/*
	 * @see ITextHoverExtension#getHoverControlCreator()
	 * @since 3.2
	 */
	@Override
	public IInformationControlCreator getHoverControlCreator() {
		if (fHoverControlCreator == null)
			fHoverControlCreator= new HoverControlCreator(getInformationPresenterControlCreator());
		return fHoverControlCreator;
	}

	private static void addLinkListener(final BrowserInformationControl control) {
		control.addLocationListener(JavaElementLinks.createLocationListener(new JavaElementLinks.ILinkHandler() {
			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks.ILinkHandler#handleJavadocViewLink(org.eclipse.jdt.core.IJavaElement)
			 */
			public void handleJavadocViewLink(IJavaElement linkTarget) {
				control.notifyDelayedInputChange(null);
				control.setVisible(false);
				control.dispose(); //FIXME: should have protocol to hide, rather than dispose
				try {
					JavadocView view= (JavadocView) JavaPlugin.getActivePage().showView(JavaUI.ID_JAVADOC_VIEW);
					view.setInput(linkTarget);
				} catch (PartInitException e) {
					JavaPlugin.log(e);
				}
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks.ILinkHandler#handleInlineJavadocLink(org.eclipse.jdt.core.IJavaElement)
			 */
			public void handleInlineJavadocLink(IJavaElement linkTarget) {
				JavadocBrowserInformationControlInput hoverInfo= getHoverInfo(new IJavaElement[] { linkTarget }, null, null, (JavadocBrowserInformationControlInput) control.getInput());
				if (control.hasDelayedInputChangeListener())
					control.notifyDelayedInputChange(hoverInfo);
				else
					control.setInput(hoverInfo);
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks.ILinkHandler#handleDeclarationLink(org.eclipse.jdt.core.IJavaElement)
			 */
			public void handleDeclarationLink(IJavaElement linkTarget) {
				control.notifyDelayedInputChange(null);
				control.dispose(); //FIXME: should have protocol to hide, rather than dispose
				try {
					//FIXME: add hover location to editor navigation history?
					JavaUI.openInEditor(linkTarget);
				} catch (PartInitException e) {
					JavaPlugin.log(e);
				} catch (JavaModelException e) {
					JavaPlugin.log(e);
				}
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks.ILinkHandler#handleExternalLink(java.net.URL, org.eclipse.swt.widgets.Display)
			 */
			public boolean handleExternalLink(URL url, Display display) {
				control.notifyDelayedInputChange(null);
				control.dispose(); //FIXME: should have protocol to hide, rather than dispose

				// Open attached Javadoc links
				OpenBrowserUtil.open(url, display);

				return true;
			}

			public void handleTextSet() {
			}
		}));
	}

	/**
	 * @deprecated see {@link org.eclipse.jface.text.ITextHover#getHoverInfo(ITextViewer, IRegion)}
	 */
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		JavadocBrowserInformationControlInput info= (JavadocBrowserInformationControlInput) getHoverInfo2(textViewer, hoverRegion);
		return info != null ? info.getHtml() : null;
	}

	/*
	 * @see org.eclipse.jface.text.ITextHoverExtension2#getHoverInfo2(org.eclipse.jface.text.ITextViewer, org.eclipse.jface.text.IRegion)
	 */
	@Override
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		return internalGetHoverInfo(textViewer, hoverRegion);
	}

	private JavadocBrowserInformationControlInput internalGetHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		IJavaElement[] elements= getJavaElementsAt(textViewer, hoverRegion);
		if (elements == null || elements.length == 0)
			return null;

		return getHoverInfo(elements, getEditorInputJavaElement(), hoverRegion, null);
	}

	/**
	 * Computes the hover info.
	 *
	 * @param elements the resolved elements
	 * @param editorInputElement the editor input, or <code>null</code>
	 * @param hoverRegion the text range of the hovered word, or <code>null</code>
	 * @param previousInput the previous input, or <code>null</code>
	 * @return the HTML hover info for the given element(s) or <code>null</code> if no information is available
	 * @since 3.4
	 */
	private static JavadocBrowserInformationControlInput getHoverInfo(IJavaElement[] elements, ITypeRoot editorInputElement, IRegion hoverRegion, JavadocBrowserInformationControlInput previousInput) {
		int nResults= elements.length;
		StringBuffer buffer= new StringBuffer();
		boolean hasContents= false;
		String base= null;
		IJavaElement element= null;

		int leadingImageWidth= 0;

		if (nResults > 1) {

			for (int i= 0; i < elements.length; i++) {
				HTMLPrinter.startBulletList(buffer);
				IJavaElement curr= elements[i];
				if (curr instanceof IMember || curr.getElementType() == IJavaElement.LOCAL_VARIABLE) {
					String label= JavaElementLabels.getElementLabel(curr, getHeaderFlags(curr));
					String link;
					try {
						String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, curr);
						link= JavaElementLinks.createLink(uri, label);
					} catch (URISyntaxException e) {
						JavaPlugin.log(e);
						link= label;
					}
					HTMLPrinter.addBullet(buffer, link);
					hasContents= true;
				}
				HTMLPrinter.endBulletList(buffer);
			}

		} else {

			element= elements[0];
			if (element instanceof IMember) {
				HTMLPrinter.addSmallHeader(buffer, getInfoText(element, editorInputElement, hoverRegion, true));
				buffer.append("<br>"); //$NON-NLS-1$
				addAnnotations(buffer, element, editorInputElement, hoverRegion);
				IMember member= (IMember) element;
				Reader reader;
				try {
//					reader= JavadocContentAccess.getHTMLContentReader(member, true, true);
					String content= JavadocContentAccess2.getHTMLContent(member, true);
					reader= content == null ? null : new StringReader(content);

					// Provide hint why there's no Javadoc
					if (reader == null && member.isBinary()) {
						boolean hasAttachedJavadoc= JavaDocLocations.getJavadocBaseLocation(member) != null;
						IPackageFragmentRoot root= (IPackageFragmentRoot)member.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
						boolean hasAttachedSource= root != null && root.getSourceAttachmentPath() != null;
						IOpenable openable= member.getOpenable();
						boolean hasSource= openable.getBuffer() != null;

						if (!hasAttachedSource && !hasAttachedJavadoc)
							reader= new StringReader(JavaHoverMessages.JavadocHover_noAttachments);
						else if (!hasAttachedJavadoc && !hasSource)
							reader= new StringReader(JavaHoverMessages.JavadocHover_noAttachedJavadoc);
						else if (!hasAttachedSource)
							reader= new StringReader(JavaHoverMessages.JavadocHover_noAttachedSource);
						else if (!hasSource)
							reader= new StringReader(JavaHoverMessages.JavadocHover_noInformation);

					} else {
						base= JavaDocLocations.getBaseURL(member);
					}

				} catch (JavaModelException ex) {
					reader= new StringReader(JavaHoverMessages.JavadocHover_error_gettingJavadoc);
					JavaPlugin.log(ex);
				}

				if (reader != null) {
					HTMLPrinter.addParagraph(buffer, reader);
				}
				hasContents= true;

			} else if (element.getElementType() == IJavaElement.LOCAL_VARIABLE || element.getElementType() == IJavaElement.TYPE_PARAMETER) {
				addAnnotations(buffer, element, editorInputElement, hoverRegion);
				HTMLPrinter.addSmallHeader(buffer, getInfoText(element, editorInputElement, hoverRegion, true));
				hasContents= true;
			}
			leadingImageWidth= 20;
		}

		if (!hasContents)
			return null;

		if (buffer.length() > 0) {
			HTMLPrinter.insertPageProlog(buffer, 0, JavadocHover.getStyleSheet());
			if (base != null) {
				int endHeadIdx= buffer.indexOf("</head>"); //$NON-NLS-1$
				buffer.insert(endHeadIdx, "\n<base href='" + base + "'>\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			HTMLPrinter.addPageEpilog(buffer);
			return new JavadocBrowserInformationControlInput(previousInput, element, buffer.toString(), leadingImageWidth);
		}

		return null;
	}

	private static String getInfoText(IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion, boolean allowImage) {
		long flags= getHeaderFlags(element);
		StringBuffer label= new StringBuffer(JavaElementLinks.getElementLabel(element, flags));
		
		if (element.getElementType() == IJavaElement.FIELD) {
			String constantValue= getConstantValue((IField) element, editorInputElement, hoverRegion);
			if (constantValue != null) {
				constantValue= HTMLPrinter.convertToHTMLContentWithWhitespace(constantValue);
				IJavaProject javaProject= element.getJavaProject();
				if (JavaCore.INSERT.equals(javaProject.getOption(DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ASSIGNMENT_OPERATOR, true)))
					label.append(' ');
				label.append('=');
				if (JavaCore.INSERT.equals(javaProject.getOption(DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_ASSIGNMENT_OPERATOR, true)))
					label.append(' ');
				label.append(constantValue);
			}
		}
		
//		if (element.getElementType() == IJavaElement.METHOD) {
//			IMethod method= (IMethod)element;
//			//TODO: add default value for annotation type members, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=249016
//		}

		String imageName= null;
		if (allowImage) {
			URL imageUrl= JavaPlugin.getDefault().getImagesOnFSRegistry().getImageURL(element);
			if (imageUrl != null) {
				imageName= imageUrl.toExternalForm();
			}
		}

		StringBuffer buf= new StringBuffer();
		addImageAndLabel(buf, element, imageName, 16, 16, label.toString(), 20, 2);
		return buf.toString();
	}

	private static long getHeaderFlags(IJavaElement element) {
		switch (element.getElementType()) {
			case IJavaElement.LOCAL_VARIABLE:
				return LOCAL_VARIABLE_FLAGS;
			case IJavaElement.TYPE_PARAMETER:
				return TYPE_PARAMETER_FLAGS;
			default:
				return LABEL_FLAGS;
		}
	}

	/*
	 * @since 3.4
	 */
	private static boolean isStaticFinal(IField field) {
		try {
			return JdtFlags.isFinal(field) && JdtFlags.isStatic(field);
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return false;
		}
	}

	/**
	 * Returns the constant value for the given field.
	 *
	 * @param field the field
	 * @param editorInputElement the editor input element
	 * @param hoverRegion the hover region in the editor
	 * @return the constant value for the given field or <code>null</code> if none
	 * @since 3.4
	 */
	private static String getConstantValue(IField field, ITypeRoot editorInputElement, IRegion hoverRegion) {
		if (!isStaticFinal(field))
			return null;

		ASTNode node= getHoveredASTNode(editorInputElement, hoverRegion);
		if (node == null)
			return null;
		
		Object constantValue= null;
		if (node.getNodeType() == ASTNode.SIMPLE_NAME) {
			IBinding binding= ((SimpleName)node).resolveBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE) {
				IVariableBinding variableBinding= (IVariableBinding)binding;
				if (field.equals(variableBinding.getJavaElement())) {
					constantValue= variableBinding.getConstantValue();
				}
			}
		}
		if (constantValue == null)
			return null;

		if (constantValue instanceof String) {
			return getEscapedStringLiteral((String) constantValue);

		} else if (constantValue instanceof Character) {
			String constantResult= getEscapedCharacterLiteral(((Character) constantValue).charValue());

			char charValue= ((Character) constantValue).charValue();
			String hexString= Integer.toHexString(charValue);
			StringBuffer hexResult= new StringBuffer("\\u"); //$NON-NLS-1$
			for (int i= hexString.length(); i < 4; i++) {
				hexResult.append('0');
			}
			hexResult.append(hexString);
			return formatWithHexValue(constantResult, hexResult.toString());

		} else if (constantValue instanceof Byte) {
			int byteValue= ((Byte) constantValue).intValue() & 0xFF;
			return formatWithHexValue(constantValue, "0x" + Integer.toHexString(byteValue)); //$NON-NLS-1$

		} else if (constantValue instanceof Short) {
			int shortValue= ((Short) constantValue).shortValue() & 0xFFFF;
			return formatWithHexValue(constantValue, "0x" + Integer.toHexString(shortValue)); //$NON-NLS-1$

		} else if (constantValue instanceof Integer) {
			int intValue= ((Integer) constantValue).intValue();
			return formatWithHexValue(constantValue, "0x" + Integer.toHexString(intValue)); //$NON-NLS-1$

		} else if (constantValue instanceof Long) {
			long longValue= ((Long) constantValue).longValue();
			return formatWithHexValue(constantValue, "0x" + Long.toHexString(longValue)); //$NON-NLS-1$

		} else {
			return constantValue.toString();
		}
	}

	private static ASTNode getHoveredASTNode(ITypeRoot editorInputElement, IRegion hoverRegion) {
		if (editorInputElement == null)
			return null;

		CompilationUnit unit= SharedASTProvider.getAST(editorInputElement, SharedASTProvider.WAIT_ACTIVE_ONLY, null);
		if (unit == null)
			return null;
		
		return NodeFinder.perform(unit, hoverRegion.getOffset(),	hoverRegion.getLength());
	}

	private static String getEscapedStringLiteral(String stringValue) {
		StringLiteral stringLiteral= AST.newAST(ASTProvider.SHARED_AST_LEVEL).newStringLiteral();
		stringLiteral.setLiteralValue(stringValue);
		String stringConstant= stringLiteral.getEscapedValue();
		if (stringConstant.length() > 80) {
			return stringConstant.substring(0, 80) + JavaElementLabels.ELLIPSIS_STRING;
		} else {
			return stringConstant;
		}
	}

	private static String getEscapedCharacterLiteral(char ch) {
		CharacterLiteral characterLiteral= AST.newAST(ASTProvider.SHARED_AST_LEVEL).newCharacterLiteral();
		characterLiteral.setCharValue(ch);
		return characterLiteral.getEscapedValue();
	}
	
	/**
	 * Creates and returns a formatted message for the given
	 * constant with its hex value.
	 *
	 * @param constantValue the constant value
	 * @param hexValue the hex value
	 * @return a formatted string with constant and hex values
	 * @since 3.4
	 */
	private static String formatWithHexValue(Object constantValue, String hexValue) {
		return Messages.format(JavaHoverMessages.JavadocHover_constantValue_hexValue, new String[] { constantValue.toString(), hexValue });
	}

	/**
	 * Returns the Javadoc hover style sheet with the current Javadoc font from the preferences.
	 * @return the updated style sheet
	 * @since 3.4
	 */
	private static String getStyleSheet() {
		if (fgStyleSheet == null)
			fgStyleSheet= loadStyleSheet();
		String css= fgStyleSheet;
		if (css != null) {
			FontData fontData= JFaceResources.getFontRegistry().getFontData(PreferenceConstants.APPEARANCE_JAVADOC_FONT)[0];
			css= HTMLPrinter.convertTopLevelFont(css, fontData);
		}

		return css;
	}

	/**
	 * Loads and returns the Javadoc hover style sheet.
	 * @return the style sheet, or <code>null</code> if unable to load
	 * @since 3.4
	 */
	private static String loadStyleSheet() {
		Bundle bundle= Platform.getBundle(JavaPlugin.getPluginId());
		URL styleSheetURL= bundle.getEntry("/JavadocHoverStyleSheet.css"); //$NON-NLS-1$
		if (styleSheetURL != null) {
			BufferedReader reader= null;
			try {
				reader= new BufferedReader(new InputStreamReader(styleSheetURL.openStream()));
				StringBuffer buffer= new StringBuffer(1500);
				String line= reader.readLine();
				while (line != null) {
					buffer.append(line);
					buffer.append('\n');
					line= reader.readLine();
				}
				return buffer.toString();
			} catch (IOException ex) {
				JavaPlugin.log(ex);
				return ""; //$NON-NLS-1$
			} finally {
				try {
					if (reader != null)
						reader.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}

	public static void addImageAndLabel(StringBuffer buf, IJavaElement element, String imageSrcPath, int imageWidth, int imageHeight, String label, int labelLeft, int labelTop) {
		buf.append("<div style='word-wrap: break-word; position: relative; "); //$NON-NLS-1$
		
		if (imageSrcPath != null) {
			buf.append("margin-left: ").append(labelLeft).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
			buf.append("padding-top: ").append(labelTop).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
		}

		buf.append("'>"); //$NON-NLS-1$
		if (imageSrcPath != null) {
			if (element != null) {
				try {
					String uri= JavaElementLinks.createURI(JavaElementLinks.OPEN_LINK_SCHEME, element);
					buf.append("<a href='").append(uri).append("'>");  //$NON-NLS-1$//$NON-NLS-2$
				} catch (URISyntaxException e) {
					element= null; // no link
				}
			}
			StringBuffer imageStyle= new StringBuffer("border:none; position: absolute; "); //$NON-NLS-1$
			imageStyle.append("width: ").append(imageWidth).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
			imageStyle.append("height: ").append(imageHeight).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
			imageStyle.append("left: ").append(- labelLeft - 1).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$

			// hack for broken transparent PNG support in IE 6, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=223900 :
			buf.append("<!--[if lte IE 6]><![if gte IE 5.5]>\n"); //$NON-NLS-1$
			String tooltip= element == null ? "" : "alt='" + JavaHoverMessages.JavadocHover_openDeclaration + "' "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			buf.append("<span ").append(tooltip).append("style=\"").append(imageStyle). //$NON-NLS-1$ //$NON-NLS-2$
					append("filter:progid:DXImageTransform.Microsoft.AlphaImageLoader(src='").append(imageSrcPath).append("')\"></span>\n"); //$NON-NLS-1$ //$NON-NLS-2$
			buf.append("<![endif]><![endif]-->\n"); //$NON-NLS-1$

			buf.append("<!--[if !IE]>-->\n"); //$NON-NLS-1$
			buf.append("<img ").append(tooltip).append("style='").append(imageStyle).append("' src='").append(imageSrcPath).append("'/>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			buf.append("<!--<![endif]-->\n"); //$NON-NLS-1$
			buf.append("<!--[if gte IE 7]>\n"); //$NON-NLS-1$
			buf.append("<img ").append(tooltip).append("style='").append(imageStyle).append("' src='").append(imageSrcPath).append("'/>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			buf.append("<![endif]-->\n"); //$NON-NLS-1$
			if (element != null) {
				buf.append("</a>"); //$NON-NLS-1$
			}
		}
		
		buf.append(label);
		
		buf.append("</div>"); //$NON-NLS-1$
	}

	public static void addAnnotations(StringBuffer buf, IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion) {
		if (element instanceof IAnnotatable) {
			try {
				String annotationString= getAnnotations(element, editorInputElement, hoverRegion);
				if (annotationString != null) {
					buf.append("<div style='margin-bottom: 5px;'>"); //$NON-NLS-1$
					buf.append(annotationString);
					buf.append("</div>"); //$NON-NLS-1$
				}
			} catch (JavaModelException e) {
				// no annotations this time...
				buf.append("<br>"); //$NON-NLS-1$
			} catch (URISyntaxException e) {
				// no annotations this time...
				buf.append("<br>"); //$NON-NLS-1$
			}
		}
	}

	private static String getAnnotations(IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion) throws URISyntaxException, JavaModelException {
		if (!(element instanceof IAnnotatable))
			return null;
		
		if (((IAnnotatable)element).getAnnotations().length == 0)
			return null;
		
		IBinding binding;
		ASTNode node= getHoveredASTNode(editorInputElement, hoverRegion);
		
		if (node == null) {
			ASTParser p= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
			p.setProject(element.getJavaProject());
			try {
				binding= p.createBindings(new IJavaElement[] { element }, null)[0];
			} catch (OperationCanceledException e) {
				return null;
			}
			
		} else {
			binding= resolveBinding(node);
		}
		
		if (binding == null)
			return null;
		
		IAnnotationBinding[] annotations= binding.getAnnotations();
		if (annotations.length == 0)
			return null;
		
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < annotations.length; i++) {
			//TODO: skip annotations that don't have an @Documented annotation?
			addAnnotation(buf, element, annotations[i]);
			buf.append("<br>"); //$NON-NLS-1$
		}
		
		return buf.toString();
	}

	private static IBinding resolveBinding(ASTNode node) {
		if (node instanceof SimpleName) {
			// workaround for https://bugs.eclipse.org/62605 (constructor name resolves to type, not method)
			SimpleName simpleName= (SimpleName) node;
			StructuralPropertyDescriptor loc= simpleName.getLocationInParent();
			while (loc == QualifiedType.NAME_PROPERTY || loc == QualifiedName.NAME_PROPERTY|| loc == SimpleType.NAME_PROPERTY || loc == ParameterizedType.TYPE_PROPERTY) {
				node= node.getParent();
				loc= node.getLocationInParent();
			}
			if (loc == ClassInstanceCreation.TYPE_PROPERTY) {
				ClassInstanceCreation cic= (ClassInstanceCreation) node.getParent();
				IMethodBinding constructorBinding= cic.resolveConstructorBinding();
				if (constructorBinding == null)
					return null;
				ITypeBinding declaringClass= constructorBinding.getDeclaringClass();
				if (!declaringClass.isAnonymous())
					return constructorBinding;
				ITypeBinding superTypeDeclaration= declaringClass.getSuperclass().getTypeDeclaration();
				return resolveSuperclassConstructor(superTypeDeclaration, constructorBinding);
			}
			return simpleName.resolveBinding();
			
		} else if (node instanceof SuperConstructorInvocation) {
			return ((SuperConstructorInvocation) node).resolveConstructorBinding();
		} else if (node instanceof ConstructorInvocation) {
			return ((ConstructorInvocation) node).resolveConstructorBinding();
		} else {
			return null;
		}
	}

	private static IBinding resolveSuperclassConstructor(ITypeBinding superClassDeclaration, IMethodBinding constructor) {
		IMethodBinding[] methods= superClassDeclaration.getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			IMethodBinding method= methods[i];
			if (method.isConstructor() && constructor.isSubsignature(method))
				return method;
		}
		return null;
	}

	private static void addAnnotation(StringBuffer buf, IJavaElement element, IAnnotationBinding annotation) throws URISyntaxException {
		String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, annotation.getAnnotationType().getJavaElement());
		buf.append('@');
		addLink(buf, uri, annotation.getName());
		
		IMemberValuePairBinding[] mvPairs= annotation.getDeclaredMemberValuePairs();
		if (mvPairs.length > 0) {
			buf.append('(');
			for (int j= 0; j < mvPairs.length; j++) {
				if (j > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				IMemberValuePairBinding mvPair= mvPairs[j];
				String memberURI= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, mvPair.getMethodBinding().getJavaElement());
				addLink(buf, memberURI, mvPair.getName());
				buf.append('=');
				addValue(buf, element, mvPair.getValue());
			}
			buf.append(')');
		}
	}

	private static void addValue(StringBuffer buf, IJavaElement element, Object value) throws URISyntaxException {
		if (value instanceof ITypeBinding) {
			ITypeBinding typeBinding= (ITypeBinding)value;
			IJavaElement type= typeBinding.getJavaElement();
			if (type == null) {
				buf.append(typeBinding.getName());
			} else {
				String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, type);
				String name= type.getElementName();
				addLink(buf, uri, name);
			}
			buf.append(".class"); //$NON-NLS-1$
			
		} else if (value instanceof IVariableBinding) { // only enum constants
			IVariableBinding variableBinding= (IVariableBinding)value;
			IJavaElement variable= variableBinding.getJavaElement();
			String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, variable);
			String name= variable.getElementName();
			addLink(buf, uri, name);
				
		} else if (value instanceof IAnnotationBinding) {
			IAnnotationBinding annotationBinding= (IAnnotationBinding)value;
			addAnnotation(buf, element, annotationBinding);
			
		} else if (value instanceof String) {
			buf.append(getEscapedStringLiteral((String)value));
			
		} else if (value instanceof Character) {
			buf.append(getEscapedCharacterLiteral(((Character)value).charValue()));
			
		} else if (value instanceof Object[]) {
			Object[] values= (Object[])value;
			buf.append('{');
			for (int i= 0; i < values.length; i++) {
				if (i > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				addValue(buf, element, values[i]);
			}
			buf.append('}');
			
		} else { // primitive types (except char) or null
			buf.append(String.valueOf(value));
		}
	}

	private static StringBuffer addLink(StringBuffer buf, String uri, String label) {
		return buf.append(JavaElementLinks.createLink(uri, label));
	}
}
