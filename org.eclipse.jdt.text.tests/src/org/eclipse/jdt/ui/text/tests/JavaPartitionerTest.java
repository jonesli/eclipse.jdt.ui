/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.text.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.jface.preference.PreferenceStore;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IDocumentPartitioningListener;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TypedRegion;

import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.text.JavaPartitionScanner;



public class JavaPartitionerTest extends TestCase {
	
	private JavaTextTools fTextTools;
	private Document fDocument;
	protected boolean fDocumentPartitioningChanged;
	
	
	public JavaPartitionerTest(String name) {
		super(name);
	}
	
	protected void setUp() {

		fTextTools= new JavaTextTools(new PreferenceStore());
		
		fDocument= new Document();
		IDocumentPartitioner partitioner= fTextTools.createDocumentPartitioner();
		partitioner.connect(fDocument);
		fDocument.setDocumentPartitioner(partitioner);
		fDocument.set("xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx");
		
		fDocumentPartitioningChanged= false;
		fDocument.addDocumentPartitioningListener(new IDocumentPartitioningListener() {
			public void documentPartitioningChanged(IDocument document) {
				fDocumentPartitioningChanged= true;
			}
		});	
	}
	
	public static Test suite() {
		return new TestSuite(JavaPartitionerTest.class); 
	}
	
	protected void tearDown () {
		fTextTools.dispose();
		fTextTools= null;
		
		IDocumentPartitioner partitioner= fDocument.getDocumentPartitioner();
		partitioner.disconnect();
		fDocument= null;
	}

	protected String print(ITypedRegion r) {
		return "[" + r.getOffset() + "," + r.getLength() + "," + r.getType() + "]";
	}
	
	protected void checkPartitioning(ITypedRegion[] expectation, ITypedRegion[] result) {
		
		assertTrue("invalid number of partitions", expectation.length == result.length);
		
		for (int i= 0; i < expectation.length; i++) {
			ITypedRegion e= expectation[i];
			ITypedRegion r= result[i];
			assertTrue(print(r) + " != " + print(e), r.equals(e));
		}
				
	}
	
	public void testInitialPartitioning() {
		try {
			
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(38, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(43, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
		
	public void testIntraPartitionChange() {
		try {
			
			fDocument.replace(34, 3, "y");
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\ny\n/***/\nxxx");
			
			assertTrue(!fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 3, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(36, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(41, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}

	public void testIntraPartitionChange2() {
		try {
			
			fDocument.replace(41, 0, "yyy");
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/**yyy*/\nxxx");
			
			// assertTrue(!fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(38, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(46, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}
	public void testInsertNewPartition() {
		try {
			
			fDocument.replace(35, 1, "/***/");
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nx/***/x\n/***/\nxxx");
			
			assertTrue(fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 2, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(35, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(40, 2, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(42, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(47, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}	

	public void testRemovePartition1() {
		try {
			
			fDocument.replace(13, 16, null);
			//	"xxx\n/*xxx*/\nx/**/\nxxx\n/***/\nxxx");
			
			assertTrue(fDocumentPartitioningChanged);
			
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 2, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(13, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(17, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(22, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(27, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testRemovePartition2() {
		
		testJoinPartition3();
		fDocumentPartitioningChanged= false;
		
		try {
			
			fDocument.replace(5, 2, null);
			//	"xxx\nxxx\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  12, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(12,  8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(20, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(25, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(29, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(34, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(39, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}

	
	public void testJoinPartitions1() {
		try {
			
			fDocument.replace(31, 1, null);
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/*/\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 13, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(42, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testJoinPartitions2() {
		try {
			
			fDocument.replace(32, 1, null);
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 13, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(42, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testJoinPartition3() {
		try {
			
			fDocument.replace(9, 2, null);
			//	"xxx\n/*xxx\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  18, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(22, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(27, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(31, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(36, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(41, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	
	public void testSplitPartition1() {
		
		testJoinPartitions1();
		fDocumentPartitioningChanged= false;
		
		
		try {
			
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/*/\nxxx\n/***/\nxxx"
			fDocument.replace(31, 0, "*");
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
		
		testInitialPartitioning();
	}
	
	public void testSplitPartition2() {
		
		testJoinPartitions2();
		fDocumentPartitioningChanged= false;
		
		try {
			
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**\nxxx\n/***/\nxxx"
			fDocument.replace(32, 0, "/");
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
			
		testInitialPartitioning();
	}
	
	public void testSplitPartition3() {
		
		fDocumentPartitioningChanged= false;
		
		try {
			
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			fDocument.replace(12, 9, "");
			//	"xxx\n/*xxx*/\nx*/\nxxx\n/**/\nxxx\n/***/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 9, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(20, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(34, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testCorruptPartitioning1() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "/***/\n/***/");
						
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(5, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(6, 5, JavaPartitionScanner.JAVA_DOC)
			};
						
			checkPartitioning(expectation, result);
			
			fDocument.replace(6, 0, "*/\n/***/\n/*");
			// "/***/\n*/\n/***/\n/*/***/"

			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(5, 4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(9, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(14, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(15, 7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};		
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}
	
	public void testCorruptPartitioning2() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "/***/\n/***/\n/***/");
						
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(5, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(6, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(11, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(12, 5, JavaPartitionScanner.JAVA_DOC)
			};
						
			checkPartitioning(expectation, result);
			
			fDocument.replace(6, 0, "*/\n/***/\n/*");
			// "/***/\n*/\n/***/\n/*/***/\n/***/"

			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(5, 4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(9, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(14, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(15, 7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(22, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(23, 5, JavaPartitionScanner.JAVA_DOC)
			};		
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}
	
	public void testCorruptPartitioning3() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "/***/\n/**/");
						
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(5, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(6, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};
						
			checkPartitioning(expectation, result);
			
			fDocument.replace(0, 9, "/***/\n/***/\n/***/\n/**");
			// "/***/\n/***/\n/***/\n/***/"

			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(5, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(6, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(11, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(12, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(17, 1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(18, 5, JavaPartitionScanner.JAVA_DOC)
			};		
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}

	public void testOpenPartition1() {
		try {
			
			fDocument.replace(42, 1, null);
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);

			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(38, 8, JavaPartitionScanner.JAVA_DOC)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testOpenPartition2() {
		try {
			
			fDocument.replace(47, 0, "/*");
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/***/\nxxx/*"
			
			assertTrue(fDocumentPartitioningChanged);

			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(38, 5, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(43, 4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(47, 2, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	
	public void testChangeContentTypeOfPartition() {
		try {
			
			fDocument.replace(39, 1, null);
			//	"xxx\n/*xxx*/\nxxx\n/**xxx*/\nxxx\n/**/\nxxx\n/**/\nxxx"
			
			assertTrue(fDocumentPartitioningChanged);
			
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(4,  7, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(16, 8, JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(24, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(29, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(33, 5, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(38, 4, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(42, 4, IDocument.DEFAULT_CONTENT_TYPE)
			};
			
			checkPartitioning(expectation, result);
		} catch (BadLocationException x) {
			assertTrue(false);
		}	
	}
	
	public void testPartitionFinder() {
		try {
			
			ITypedRegion[] partitioning= fDocument.computePartitioning(0, fDocument.getLength());
			
			for (int i= 0; i < partitioning.length; i++) {
				ITypedRegion expected= partitioning[i];
				for (int j= 0; j < expected.getLength(); j++) {
					ITypedRegion result= fDocument.getPartition(expected.getOffset() + j);
					assertTrue(expected.equals(result));
				}
			}
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testExtendPartition() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "/*");
						
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  2, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};
						
			checkPartitioning(expectation, result);
			
			fDocument.replace(2, 0, " ");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  3, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};
			
			checkPartitioning(expectation, result);
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testTransformPartition() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "/*");
						
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  2, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};
						
			checkPartitioning(expectation, result);
			
			fDocument.replace(2, 0, "*");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  3, JavaPartitionScanner.JAVA_DOC)
			};
			
			checkPartitioning(expectation, result);
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testTogglePartition() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "\t/*\n\tx\n\t/*/\n\ty\n//\t*/");

			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation1= {
				new TypedRegion(0,  1, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(1,  10, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(11, 4, IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(15, 5, JavaPartitionScanner.JAVA_SINGLE_LINE_COMMENT)
			};			
			checkPartitioning(expectation1, result);
			
			fDocumentPartitioningChanged= false;
			fDocument.replace(0, 0, "//"); // "//\t/*\n\tx\n\t/*/\n\ty\n//\t*/"
			assertTrue(fDocumentPartitioningChanged);
			
			result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation2= {
				new TypedRegion(0,  6, JavaPartitionScanner.JAVA_SINGLE_LINE_COMMENT),
				new TypedRegion(6,  4,  IDocument.DEFAULT_CONTENT_TYPE),
				new TypedRegion(10,  12, JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};			
			checkPartitioning(expectation2, result);
					
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testEditing1() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "");
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  0,  IDocument.DEFAULT_CONTENT_TYPE)
			};			
			checkPartitioning(expectation, result);
			
			fDocument.replace(fDocument.getLength(), 0, "/");
			fDocument.replace(fDocument.getLength(), 0, "*");
			fDocument.replace(fDocument.getLength(), 0, "*");
			fDocument.replace(fDocument.getLength(), 0, "/");
			
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  fDocument.getLength(),  JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};			
			checkPartitioning(expectation, result);			
			
			
			fDocument.replace(fDocument.getLength(), 0, "\r\n");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  4,  JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT),
				new TypedRegion(4, 2, IDocument.DEFAULT_CONTENT_TYPE)
			};			
			checkPartitioning(expectation, result);			
			
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testEditing2() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "");
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  0,  IDocument.DEFAULT_CONTENT_TYPE)
			};			
			checkPartitioning(expectation, result);
			
			fDocument.replace(fDocument.getLength(), 0, "/");
			fDocument.replace(fDocument.getLength(), 0, "*");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  fDocument.getLength(),  JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};			
			checkPartitioning(expectation, result);			
			
			fDocument.replace(fDocument.getLength(), 0, "\r\n");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  fDocument.getLength(),  JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};			
			checkPartitioning(expectation, result);
			
			fDocument.replace(fDocument.getLength(), 0, "*");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  fDocument.getLength(),  JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};			
			checkPartitioning(expectation, result);
			
			fDocument.replace(fDocument.getLength(), 0, "*");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  fDocument.getLength(),  JavaPartitionScanner.JAVA_MULTI_LINE_COMMENT)
			};			
			checkPartitioning(expectation, result);			
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
	
	public void testEditing3() {
		try {
			
			fDocument.replace(0, fDocument.getLength(), "");
			
			ITypedRegion[] result= fDocument.computePartitioning(0, fDocument.getLength());
			TypedRegion[] expectation= {
				new TypedRegion(0,  0,  IDocument.DEFAULT_CONTENT_TYPE)
			};			
			checkPartitioning(expectation, result);
			
			fDocument.replace(fDocument.getLength(), 0, "/");
			fDocument.replace(fDocument.getLength(), 0, "*");
			fDocument.replace(fDocument.getLength(), 0, "*");
			fDocument.replace(fDocument.getLength(), 0, "\r\n *");
			fDocument.replace(fDocument.getLength(), 0, "/");


			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  fDocument.getLength(),  JavaPartitionScanner.JAVA_DOC)
			};			
			checkPartitioning(expectation, result);			
			
			fDocument.replace(fDocument.getLength(), 0, "*");
			result= fDocument.computePartitioning(0, fDocument.getLength());
			expectation= new TypedRegion[] {
				new TypedRegion(0,  8,  JavaPartitionScanner.JAVA_DOC),
				new TypedRegion(8, 1, IDocument.DEFAULT_CONTENT_TYPE)
			};			
			checkPartitioning(expectation, result);		
			
		} catch (BadLocationException x) {
			assertTrue(false);
		}
	}
}
