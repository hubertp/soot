/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Jennifer Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package ca.mcgill.sable.soot.launching;

import org.eclipse.ui.*;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.action.*;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import java.lang.reflect.InvocationTargetException;
import ca.mcgill.sable.soot.*;
import ca.mcgill.sable.soot.attributes.AbstractAttributesComputer;
import ca.mcgill.sable.soot.attributes.JavaAttributesComputer;
import ca.mcgill.sable.soot.attributes.JimpleAttributesComputer;
import ca.mcgill.sable.soot.attributes.SootAttributesJavaColorer;
import ca.mcgill.sable.soot.editors.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.jdt.core.*;
import ca.mcgill.sable.soot.cfg.*;
import ca.mcgill.sable.graph.testing.*;
import ca.mcgill.sable.graph.*;

import java.util.*;
import soot.jimple.toolkits.annotation.callgraph.*;
			
/**
 * Main Soot Launcher. Handles running Soot directly (or as a 
 * process) 
 */
public abstract class SootLauncher  implements IWorkbenchWindowActionDelegate {
	
	private IWorkbenchPart part;
 	protected IWorkbenchWindow window;
	private ISelection selection;
	private IStructuredSelection structured;
	protected String platform_location;
	protected String external_jars_location;
	public SootClasspath sootClasspath = new SootClasspath();
	public SootSelection sootSelection;
	//private IFolder sootOutputFolder;
	private SootCommandList sootCommandList;
	private String outputLocation;
	private SootDefaultCommands sdc;
	private SootOutputFilesHandler fileHandler;
	private DavaHandler davaHandler;
	private ArrayList cfgList;
	
	public void run(IAction action) {
		
		setSootSelection(new SootSelection(structured));		
 		getSootSelection().initialize(); 		
		setFileHandler(new SootOutputFilesHandler(window));
		getFileHandler().resetSootOutputFolder(getSootSelection().getProject());		
		//System.out.println("starting SootLauncher");
		setDavaHandler(new DavaHandler());
		getDavaHandler().setSootOutputFolder(getFileHandler().getSootOutputFolder());
		getDavaHandler().handleBefore();
		initPaths();
		initCommandList();

		
	}
	
	
	private void initCommandList() {
		setSootCommandList(new SootCommandList());
	}
	
	protected void runSootDirectly(){
		runSootDirectly("soot.Main");
	}
	
	private void sendSootOutputEvent(String toSend){
		SootOutputEvent send = new SootOutputEvent(this, ISootOutputEventConstants.SOOT_NEW_TEXT_EVENT);
		send.setTextToAppend(toSend);
		final SootOutputEvent sendFinal = send;
		
		Display.getCurrent().asyncExec(new Runnable(){
			public void run() {
				SootPlugin.getDefault().fireSootOutputEvent(sendFinal);
			};
		});
	}
	
	protected void runSootDirectly(String mainClass) {
		
		int length = getSootCommandList().getList().size();
		//Object [] temp = getSootCommandList().getList().toArray();
		String temp [] = new String [length];
		
		getSootCommandList().getList().toArray(temp);
		
		sendSootOutputEvent(mainClass);
		sendSootOutputEvent(" ");
		
		final String [] cmdAsArray = temp;
		
		for (int i = 0; i < temp.length; i++) {
			
			//System.out.println(temp[i]);
			sendSootOutputEvent(temp[i]);
			sendSootOutputEvent(" ");
		}
		sendSootOutputEvent("\n");
		
		
		//System.out.println("about to make list be array of strings");
		//final String [] cmdAsArray = (String []) temp;
		IRunnableWithProgress op; 
		try {   
        	newProcessStarting();
            op = new SootRunner(temp, Display.getCurrent(), mainClass);
           	((SootRunner)op).setParent(this);
            ModalContext.run(op, true, new NullProgressMonitor(), Display.getCurrent());
            //setCfgList(((SootRunner)op).getCfgList());
 		} 
 		catch (InvocationTargetException e1) {
    		// handle exception
    		System.out.println("InvocationTargetException: "+e1.getMessage());
 		} 
 		catch (InterruptedException e2) {
    		// handle cancelation
    		System.out.println("InterruptedException: "+e2.getMessage());
    		//op.getProc().destroy();
 		}

	}
	
	/*public boolean handleNewAnalysis(String name){
		IWorkbenchWindow window = SootPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow();
		
		Shell shell = Display.getCurrent().getActiveShell();
		if (shell == null){
			System.out.println("shell is null");
		}
		MessageDialog msgDialog = new MessageDialog(shell, "Interaction Question",  null,"Do you want to interact with analysis: "+name+" ?",0, new String []{"Yes", "No"}, 0);
		msgDialog.open();
		boolean result = msgDialog.getReturnCode() == 0 ? true: false;
		return result;
	}*/
	
	protected void runSootAsProcess(String cmd) {
		
        SootProcessRunner op;    
        try {   
        	newProcessStarting();
            op = new SootProcessRunner(Display.getCurrent(), cmd, sootClasspath);

            if (window == null) {
            	window = SootPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow();
            }
            new  ProgressMonitorDialog(window.getShell()).run(true, true, op);
 			
 			
 		} 
 		catch (InvocationTargetException e1) {
    		// handle exception
 		} 
 		catch (InterruptedException e2) {
    		// handle cancelation
    		System.out.println(e2.getMessage());
    		//op.getProc().destroy();
 		}
 
   
        
	}
	private void newProcessStarting() {
		SootOutputEvent clear_event = new SootOutputEvent(this, ISootOutputEventConstants.SOOT_CLEAR_EVENT);
        SootPlugin.getDefault().fireSootOutputEvent(clear_event);    
        SootOutputEvent se = new SootOutputEvent(this, ISootOutputEventConstants.SOOT_NEW_TEXT_EVENT);
        se.setTextToAppend("Starting ...");
        SootPlugin.getDefault().fireSootOutputEvent(se);
	}
			
	private String[] formCmdLine(String cmd) {
	 
	  	
	   	StringTokenizer st = new StringTokenizer(cmd);
	  	int count = st.countTokens();
        String[] cmdLine = new String[count];        
        count = 0;
        
        while(st.hasMoreTokens()) {
            cmdLine[count++] = st.nextToken();
            //System.out.println(cmdLine[count-1]); 
        }
        
        return cmdLine; 
	}
	
	private void initPaths() {
		
		sootClasspath.initialize();
		
		// platform location 
		//platform_location = Platform.getLocation().toOSString();
		platform_location = getSootSelection().getJavaProject().getProject().getLocation().toOSString();
		//System.out.println("platform_location: "+platform_location);
		platform_location = platform_location.substring(0, platform_location.lastIndexOf(System.getProperty("file.separator")));
        //System.out.println("platform_location: "+platform_location);
		// external jars location - may need to change don't think I use this anymore
		//external_jars_location = Platform.getLocation().removeLastSegments(2).toOSString();
		setOutputLocation(platform_location+getFileHandler().getSootOutputFolder().getFullPath().toOSString());
		
	}
	
	protected void addJars(){
		try {
	
			IPackageFragmentRoot [] roots = getSootSelection().getJavaProject().getAllPackageFragmentRoots();
			
			for (int i = 0; i < roots.length; i++){
				//System.out.println("root: "+roots[i]);
				//System.out.println("root kind: "+roots[i].getKind());
				//System.out.println("root path: "+roots[i].getPath());
				
				
				if (roots[i].isArchive()){
					if (roots[i].getResource() != null){
					
						//System.out.println("resource: "+roots[i].getResource());
						//System.out.println("Jar File: "+platform_location+roots[i].getResource().getFullPath().toOSString());
						setClasspathAppend(platform_location+roots[i].getPath().toOSString());

					}
					else {

						//System.out.println("Jar File: "+roots[i].getPath().toOSString());
						setClasspathAppend(roots[i].getPath().toOSString());

					}
					//System.out.println("Jar File Kind: "+roots[i].getRawClasspathEntry().getEntryKind());
					//System.out.println("Jar File raw classpath entry: "+roots[i].getRawClasspathEntry());
					
					//if (roots[i].getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_LIBRARY){
						//setClasspathAppend(roots[i].getPath().toOSString());
					//}
				}
			}
		}
		catch (JavaModelException e){
		}
	}
	
	public abstract void setClasspathAppend(String ca);
	
	public void runFinish() {
		getFileHandler().refreshFolder();
		getFileHandler().refreshAll(getSootSelection().getProject());
		//for updating markers
		SootPlugin.getDefault().getManager().updateSootRanFlag();
		//getDavaHandler().handleAfter();
		//getFileHandler().handleFilesChanged();
		final IEditorPart activeEdPart = SootPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		/*if ((activeEdPart != null) && (activeEdPart instanceof JimpleEditor)){
            if (activeEdPart.getEditorInput() != null){
                System.out.println("will update ed first");
                    
                activeEdPart.getSite().getShell().getDisplay().asyncExec(new Runnable(){
                    public void run() {
                        ((AbstractTextEditor)activeEdPart).setInput(activeEdPart.getEditorInput());
                    };
                });
            }
		}*/
		SootPlugin.getDefault().getPartManager().updatePart(activeEdPart);
		//System.out.println("after update part");
		// run cfgviewer
		//System.out.println("call graph list: "+getCfgList());
		if (getCfgList() != null){
			// currently this is the call graph list of pkgs
			//System.out.println("callgraph list not null");
			GraphGenerator generator = new GraphGenerator();
			//System.out.println("new generator made");
			generator.setChildren(convertPkgList(getCfgList()));
			GraphPlugin.getDefault().setGenerator(generator);
		
			generator.run(null);
			
			
			/*Iterator it = getCfgList().iterator();
			while (it.hasNext()){
				ModelCreator mc = new ModelCreator();
				System.out.println("struct: "+getStructured().getFirstElement().getClass());
				//mc.setResource((IResource)getStructured().getFirstElement());
				mc.setSootGraph((soot.toolkits.graph.DirectedGraph)it.next());
				//mc.createModel();
				mc.displayModel();
			
			}*/
		}
		//CFGViewer cv;
		//Iterator it = getCfgList().iterator();
		//while (it.hasNext()){
		//	cv = new CFGViewer();
		//	cv.run(it.next());
		//}
	}
	
	HashMap alreadyDone = new HashMap();
	
	private ArrayList convertPkgList(ArrayList pkgList){
		ArrayList conList = new ArrayList();
		Iterator it = pkgList.iterator();
		while(it.hasNext()){
			CallData cd = (CallData)it.next();
			System.out.println("cd: "+cd);
			TestNode tn = null;
			if (alreadyDone.containsKey(cd)){
				System.out.println("already done: "+cd);
				tn = (TestNode)alreadyDone.get(cd);
			}
			else {
				tn = new TestNode();
				tn.setData(cd.getData());
				alreadyDone.put(cd, tn);
				if (cd.getChildren().size() != 0){
					tn.setChildren(convertPkgList(cd.getChildren()));
				}
				if (cd.getOutputs().size() != 0){
					tn.setOutputs(convertPkgList(cd.getOutputs()));
				}
			}
			conList.add(tn);
			
		}
		
		return conList;
	}
	

	public IStructuredSelection getStructured() {
		return structured;
	}
		
	private void setStructured(IStructuredSelection struct) {
		structured = struct;
	}
	
	public void init(IWorkbenchWindow window) {
 		this.window = window;
 	}
 	
 	public void dispose() {
 	}
 	
 	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;	
		
		if (selection instanceof IStructuredSelection){
			setStructured((IStructuredSelection)selection);
		}
	}
	
	/**
	 * Returns the sootClasspath.
	 * @return SootClasspath
	 */
	public SootClasspath getSootClasspath() {
		return sootClasspath;
	}

	/**
	 * Sets the sootClasspath.
	 * @param sootClasspath The sootClasspath to set
	 */
	public void setSootClasspath(SootClasspath sootClasspath) {
		this.sootClasspath = sootClasspath;
	}

	/**
	 * Returns the sootSelection.
	 * @return SootSelection
	 */
	public SootSelection getSootSelection() {
		return sootSelection;
	}

	/**
	 * Sets the sootSelection.
	 * @param sootSelection The sootSelection to set
	 */
	public void setSootSelection(SootSelection sootSelection) {
		this.sootSelection = sootSelection;
	}

	

	/**
	 * Returns the window.
	 * @return IWorkbenchWindow
	 */
	public IWorkbenchWindow getWindow() {
		return window;
	}

	/**
	 * Returns the sootCommandList.
	 * @return SootCommandList
	 */
	public SootCommandList getSootCommandList() {
		return sootCommandList;
	}

	/**
	 * Sets the sootCommandList.
	 * @param sootCommandList The sootCommandList to set
	 */
	public void setSootCommandList(SootCommandList sootCommandList) {
		this.sootCommandList = sootCommandList;
	}

	/**
	 * Returns the output_location.
	 * @return String
	 */
	public String getOutputLocation() {
		return outputLocation;
	}

	/**
	 * Sets the output_location.
	 * @param output_location The output_location to set
	 */
	public void setOutputLocation(String outputLocation) {
		this.outputLocation = outputLocation;
	}

	/**
	 * Returns the sdc.
	 * @return SootDefaultCommands
	 */
	public SootDefaultCommands getSdc() {
		return sdc;
	}

	/**
	 * Sets the sdc.
	 * @param sdc The sdc to set
	 */
	public void setSdc(SootDefaultCommands sdc) {
		this.sdc = sdc;
	}

	/**
	 * Returns the fileHandler.
	 * @return SootOutputFilesHandler
	 */
	public SootOutputFilesHandler getFileHandler() {
		return fileHandler;
	}

	/**
	 * Sets the fileHandler.
	 * @param fileHandler The fileHandler to set
	 */
	public void setFileHandler(SootOutputFilesHandler fileHandler) {
		this.fileHandler = fileHandler;
	}

	/**
	 * @return
	 */
	public DavaHandler getDavaHandler() {
		return davaHandler;
	}

	/**
	 * @param handler
	 */
	public void setDavaHandler(DavaHandler handler) {
		davaHandler = handler;
	}

	/**
	 * @return
	 */
	public ArrayList getCfgList() {
		return cfgList;
	}

	/**
	 * @param list
	 */
	public void setCfgList(ArrayList list) {
		cfgList = list;
	}

}
