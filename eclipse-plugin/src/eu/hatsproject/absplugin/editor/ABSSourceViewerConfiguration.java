/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package eu.hatsproject.absplugin.editor;

import static eu.hatsproject.absplugin.util.Constants.*;
import static eu.hatsproject.absplugin.util.UtilityFunctions.getDefaultPreferenceStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.*;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.DefaultAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.SimpleMarkerAnnotation;

import eu.hatsproject.absplugin.console.ConsoleManager;
import eu.hatsproject.absplugin.console.ConsoleManager.MessageType;
import eu.hatsproject.absplugin.costabslink.CostabsLink;
import eu.hatsproject.absplugin.editor.contentassist.ABSCompletionProcessor;

/**
 * Configures the {@link SourceViewer} which is the main part of the {@link ABSEditor}.
 * The document is split into different partitions for comments, strings and code using
 * a {@link PresentationReconciler} which uses {@link ITokenScanner} to create the partitions.
 * Additionally the hovers for error markers and the content assist are initialized here.
 * 
 * @author mweber
 *
 */
public class ABSSourceViewerConfiguration extends SourceViewerConfiguration {
	private ABSEditor editor;
	
	public ABSSourceViewerConfiguration(ABSEditor editor){
		this.editor = editor;
	}
		
	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		DefaultDamagerRepairer dr = new ABSDamagerRepairer(new ABSSingleCommentScanner());     
		reconciler.setDamager(dr, PARTITION_SINLGE_LINE_COMMENT);
		reconciler.setRepairer(dr, PARTITION_SINLGE_LINE_COMMENT);       
		dr = new ABSDamagerRepairer(new ABSMultiCommentScanner());     
		reconciler.setDamager(dr, PARTITION_MULTI_LINE_COMMENT);
		reconciler.setRepairer(dr, PARTITION_MULTI_LINE_COMMENT);   
		dr = new ABSDamagerRepairer(new ABSStringScanner());     
		reconciler.setDamager(dr, PARTITION_STRING);
		reconciler.setRepairer(dr, PARTITION_STRING);   
		dr = new ABSDamagerRepairer(new ABSCharacterScanner());     
		reconciler.setDamager(dr, PARTITION_CHARACTER);
		reconciler.setRepairer(dr, PARTITION_CHARACTER);
		dr = new ABSDamagerRepairer(new ABSCodeScanner(new IdentifierWordDetector(), editor));
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
		return reconciler;
	}
	
	public class ABSSingleCommentScanner extends RuleBasedScanner {
		public ABSSingleCommentScanner() {
			IPreferenceStore store = getDefaultPreferenceStore();
			Color color = new Color(Display.getCurrent(),PreferenceConverter.getColor(store, SYNTAXCOLOR_COLOR + SYNTAXCOLOR_COMMENT));
			IToken token = new Token(new TextAttribute(color, null, computeAttributes(store, SYNTAXCOLOR_COMMENT)));
			IRule singleLineRule = new EndOfLineRule("//",token); 
			setRules(new IRule[] {singleLineRule});
		}
	}
	public class ABSMultiCommentScanner extends RuleBasedScanner {
		public ABSMultiCommentScanner(){
			IPreferenceStore store = getDefaultPreferenceStore();
			Color color = new Color(Display.getCurrent(),PreferenceConverter.getColor(store, SYNTAXCOLOR_COLOR + SYNTAXCOLOR_COMMENT));
			IToken token = new Token(new TextAttribute(color, null, computeAttributes(store, SYNTAXCOLOR_COMMENT)));
			IRule multiLineRule = new MultiLineRule("/*", "*/", token, '\\', true);
			setRules(new IRule[] {multiLineRule});
		}
	}
	public class ABSStringScanner extends RuleBasedScanner {
		public ABSStringScanner() {
			IPreferenceStore store = getDefaultPreferenceStore();
			Color color = new Color(Display.getCurrent(),PreferenceConverter.getColor(store, SYNTAXCOLOR_COLOR + SYNTAXCOLOR_STRING));
			IToken token = new Token(new TextAttribute(color, null, computeAttributes(store, SYNTAXCOLOR_STRING)));
			IRule singleLineRule = new SingleLineRule("\"", "\"", token, '\\');
			setRules(new IRule[] {singleLineRule});
		}
	}
	public class ABSCharacterScanner extends RuleBasedScanner {
		public ABSCharacterScanner() {
			IPreferenceStore store = getDefaultPreferenceStore();
			Color color = new Color(Display.getCurrent(),PreferenceConverter.getColor(store, SYNTAXCOLOR_COLOR + SYNTAXCOLOR_STRING));
			IToken token = new Token(new TextAttribute(color, null, computeAttributes(store, SYNTAXCOLOR_STRING)));
			IRule singleLineRule = new SingleLineRule("'", "'", token, '\\');
			setRules(new IRule[] {singleLineRule});
		}
	}
	
	private static int computeAttributes(IPreferenceStore store, String postfix) {
		int funattr = 0;

		boolean attrbold = store.getBoolean(SYNTAXCOLOR_BOLD + postfix);
		if(attrbold) funattr = funattr | SWT.BOLD;

		boolean attritalic = store.getBoolean(SYNTAXCOLOR_ITALIC + postfix);
		if(attritalic) funattr = funattr | SWT.ITALIC;

		boolean attrunderline = store.getBoolean(SYNTAXCOLOR_UNDERLINE + postfix);
		if(attrunderline) funattr = funattr | TextAttribute.UNDERLINE;

		boolean attrstrikethrough = store.getBoolean(SYNTAXCOLOR_STRIKETHROUGH + postfix);
		if(attrstrikethrough) funattr = funattr | TextAttribute.STRIKETHROUGH;
		
		return funattr;
	}
	
	public class IdentifierWordDetector implements IWordDetector{
		@Override
		public boolean isWordStart(char c){
			return isWordPart(c);
		}
		@Override
		public boolean isWordPart(char c){
			return Character.isLetter(c) || Character.isDigit(c) || c == '_';
		}
	}
	
	@Override
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return new DefaultAnnotationHover(){
			@Override
			protected boolean isIncluded(Annotation annotation) {
				if(annotation instanceof SimpleMarkerAnnotation){
					SimpleMarkerAnnotation markerannotation = (SimpleMarkerAnnotation)annotation;
					
					try {
						return markerannotation.getMarker().exists() 
							&& ( markerannotation.getMarker().isSubtypeOf(MARKER_TYPE)
									|| (markerannotation.getMarker().isSubtypeOf(LOCATION_TYPE_INFERENCE_MARKER_TYPE))
									|| (markerannotation.getMarker().isSubtypeOf(CostabsLink.MARKER_UB)));
					} catch (CoreException e) {
						e.printStackTrace(ConsoleManager.getDefault().getPrintStream(MessageType.MESSAGE_ERROR));
					}
				}
				return false;
			}
		};
	}
	
	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType){
		return new DefaultTextHover(sourceViewer){
			@Override
			protected boolean isIncluded(Annotation annotation) {
				if(annotation instanceof SimpleMarkerAnnotation){SimpleMarkerAnnotation markerannotation = (SimpleMarkerAnnotation)annotation;
					try {
						return markerannotation.getMarker().exists() 
							&& ( markerannotation.getMarker().isSubtypeOf(MARKER_TYPE) 
									|| (markerannotation.getMarker().isSubtypeOf(LOCATION_TYPE_INFERENCE_MARKER_TYPE))
									|| (markerannotation.getMarker().isSubtypeOf(CostabsLink.MARKER_UB)));
					} catch (CoreException e) {
						e.printStackTrace(ConsoleManager.getDefault().getPrintStream(MessageType.MESSAGE_ERROR));
					}
				}
				return false;
			}
		};
	}

	
	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer){
		ContentAssistant assistant = new ContentAssistant();
		assistant.setContentAssistProcessor(new ABSCompletionProcessor(editor), IDocument.DEFAULT_CONTENT_TYPE);
		assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
		assistant.enableAutoActivation(true);
		assistant.enableAutoInsert(true);
		assistant.setAutoActivationDelay(0);
		return assistant;
	}
	
	@Override
	public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
		return new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(final Shell parent) {
				final DefaultInformationControl.IInformationPresenter presenter = new DefaultInformationControl.IInformationPresenter() {
					@Override
					public String updatePresentation(Display display, String infoText, TextPresentation presentation, int maxWidth, int maxHeight) {
						StyleRange range = new StyleRange(0, infoText.length(), null, null);
						range.font = new Font(parent.getDisplay(), "Courier New", 10, SWT.NORMAL);
						presentation.addStyleRange(range);
						return infoText;
					}
				};
			return new DefaultInformationControl(parent, presenter);
			}
		};
	}
	
	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		return new IHyperlinkDetector[] {
				new AbsHyperlinkDetector(editor)
		};
	}
}