package com.amazonaws.samples;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GenericBucketRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectId;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

//import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.awt.event.*;
import java.io.*;

public class DragDropFiles extends JFrame {

    private DefaultListModel model = new DefaultListModel();
    private int count = 0;
    private JTree tree;
    private JLabel label;
    private JButton download;
    private DefaultTreeModel treeModel;
    private TreePath namesPath;
    private JPanel wrap;
    private TreePath downloadPath = null;

    private static DefaultTreeModel getDefaultTreeModel() {
    	        
    	DefaultMutableTreeNode root = null;
    	String rootBucket = null;
        //---display all buckets and contents in them
        for (Bucket bucket : s3client.listBuckets()) {
        	//s3 bucket
        	rootBucket=bucket.getName();
        	root = new DefaultMutableTreeNode(rootBucket);
        }//end for
        
        ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(rootBucket);
        ListObjectsV2Result result;     
        
        //per requirements we will only go two folders down for this homework
        DefaultMutableTreeNode parent=null; //folders directly under the root bucket
        DefaultMutableTreeNode nparent=null; //objects under any parent folder
        List<String> list = new ArrayList<String>();
        //this code follows code provided by AWS docs
        do {
        	
            result = s3client.listObjectsV2(req);

            for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
            	list.add(objectSummary.getKey());
            }//end for
            
            //check everything in the list and determine if it is a parent or nparent
            for(String str: list){
            	if(str.endsWith("/")){//the string0 is a parent object
            		parent=new DefaultMutableTreeNode(str);
            		root.add(parent);
            	}//end if
            	else{
            		try{
            			parent.add(new DefaultMutableTreeNode(s3client.getObject(rootBucket, str).getKey()));
            		}catch(Exception e){}
            	}//end else
            }//end for
            
            //Per the AWS docs....
            // If there are more than maxKeys keys in the bucket, get a continuation token
            // and list the next objects.
            String token = result.getNextContinuationToken();
            req.setContinuationToken(token);
        } while (result.isTruncated());        
           
        return new DefaultTreeModel(root);
    }//end

    public DragDropFiles() {
        super("Drag and Drop File Transfers in Cloud");

        treeModel = getDefaultTreeModel();
        
        tree = new JTree(treeModel);
        tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setDropMode(DropMode.ON);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        namesPath = tree.getPathForRow(2);
        tree.expandRow(2);
        tree.expandRow(1);
        tree.setRowHeight(0);

        //Handles the tree node selection event that triggered by user selection
        //Identify which tree node(file name) has been selected, for downloading.
        //For more info, see TreeSelectionListener Class in Java
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
            	String bucket=e.getPath().getPath()[0].toString();
            	String toDownload = e.getNewLeadSelectionPath().getLastPathComponent().toString();
                
                downloadPath = new TreePath(bucket+"/"+toDownload);
                //S3Object object = s3client.getObject(new GetObjectRequest(bucket,toDownload));
            }
        });
        
        tree.setTransferHandler(new TransferHandler() {

            public boolean canImport(TransferHandler.TransferSupport info) {
                // we'll only support drops (not clip-board paste)
                if (!info.isDrop()) {
                    return false;
                }
                info.setDropAction(COPY); //Tony added
                info.setShowDropLocation(true);
                // we import Strings and files
                if (!info.isDataFlavorSupported(DataFlavor.stringFlavor) &&
                		!info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }

                // fetch the drop location
                JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
                TreePath path = dl.getPath();

                // we don't support invalid paths or descendants of the names folder
                if (path == null || namesPath.isDescendant(path)) {
                    return false;
                }
                return true;
            }

            public boolean importData(TransferHandler.TransferSupport info) {            	
            		
            	// if we can't handle the import, say so
                if (!canImport(info)) {
                    return false;
                }
                // fetch the drop location
                JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
                
                // fetch the path and child index from the drop location
                TreePath path = dl.getPath();
                int childIndex = dl.getChildIndex();
                
                // fetch the data and bail if this fails
                String uploadName = "";
                
                Transferable t = info.getTransferable();
                try {
                    java.util.List<File> l =
                        (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);

                    for (File f : l) {
                    		uploadName = f.getName();
                    		String copyName = "./copy-" + f.getName();
                    		File destFile = new File(copyName);
                    		copyFile(f, destFile);
                        	String bucket=dl.getPath().getPath()[0].toString();
                        	String toUpload = dl.getPath().getLastPathComponent().toString();
                            PutObjectRequest request = new PutObjectRequest(bucket+"/"+toUpload,uploadName,f);
                            s3client.putObject(request);
                        break;//We process only one dropped file.
                    }
                } catch (UnsupportedFlavorException e) {
                    return false;
                } catch (IOException e) {
                    return false;
                }
                
                // if child index is -1, the drop was on top of the path, so we'll
                // treat it as inserting at the end of that path's list of children
                if (childIndex == -1) {
                    childIndex = tree.getModel().getChildCount(path.getLastPathComponent());
                }

                // create a new node to represent the data and insert it into the model
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(uploadName);
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)path.getLastPathComponent();
                treeModel.insertNodeInto(newNode, parentNode, childIndex);

                // make the new node visible and scroll so that it's visible
                tree.makeVisible(path.pathByAddingChild(newNode));
                tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));
				
                //Display uploading status
                label.setText("UpLoaded **" + uploadName + "** successfully!");

                return true;
            }
            
        });

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        this.wrap = new JPanel();
        this.label = new JLabel("Status Bar...");
        wrap.add(this.label);
        p.add(Box.createHorizontalStrut(4));
        p.add(Box.createGlue());
        p.add(wrap);
        p.add(Box.createGlue());
        p.add(Box.createHorizontalStrut(4));
        getContentPane().add(p, BorderLayout.NORTH);

        getContentPane().add(new JScrollPane(tree), BorderLayout.CENTER);
        download = new JButton("Download");
        download.addActionListener(new ActionListener() { 
        	  public void actionPerformed(ActionEvent e) { 
        	    //You have to program here in this method in response to downloading a file from the cloud,
        		//Refer to TreePath class about how to extract the bucket name and file name out of 
        		//the downloadPath object.
        	    if(downloadPath != null) {
        	    	String dlpath=downloadPath.toString();
        	    	dlpath=dlpath.replace("[","");
        	    	dlpath=dlpath.replace("]","");
        	    	String fileName =dlpath.substring(dlpath.lastIndexOf("/"));
        	    	File file =new File("./"+fileName);
        	    	s3client.getObject(new GetObjectRequest(dlpath.split("/",2)[0],dlpath.split("/",2)[1]),file);
        	    	System.out.println("File downloaded to: "+file.getAbsolutePath());
                    //Display downloading status
                    label.setText("DownLoaded **" + fileName + "** successfully!");
        	    }
        	  } 
        	} );

        p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        wrap = new JPanel();
        //wrap.add(new JLabel("Show drop location:"));
        wrap.add(download);
        p.add(Box.createHorizontalStrut(4));
        p.add(Box.createGlue());
        p.add(wrap);
        p.add(Box.createGlue());
        p.add(Box.createHorizontalStrut(4));
        getContentPane().add(p, BorderLayout.SOUTH);

        getContentPane().setPreferredSize(new Dimension(400, 450));
    }

    private static void increaseFont(String type) {
        Font font = UIManager.getFont(type);
        font = font.deriveFont(font.getSize() + 4f);
        UIManager.put(type, font);
    }

    private static void createAndShowGUI() {
        //Create and set up the window.
        DragDropFiles test = new DragDropFiles();
        test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        //Display the window.
        test.pack();
        test.setVisible(true);
    }
    
    
    private void copyFile(File source, File dest)
    		throws IOException {
	    	InputStream input = null;
	    	OutputStream output = null;
	    	try {
	    		input = new FileInputStream(source);
	    		output = new FileOutputStream(dest);
	    		byte[] buf = new byte[1024];
	    		int bytesRead;
	    		while ((bytesRead = input.read(buf)) > 0) {
	    			output.write(buf, 0, bytesRead);
	    		}
	    	} finally {
	    		input.close();
	    		output.close();
	    	}
    }

	//AWS
	static AWSCredentials credentials = new BasicAWSCredentials("AKIAIO6BCRZ4I6OERWUA", "KdBQQOzaZXIH23Wzg59IvGXLWMXX4K+LuDVTdQke");
	
	//create s3 client
	@SuppressWarnings("deprecation")
	static
	AmazonS3 s3client = new AmazonS3Client(credentials);
    
    public static void main(String[] args) {   	
    	
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {                
                try {
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                    increaseFont("Tree.font");
                    increaseFont("Label.font");
                    increaseFont("ComboBox.font");
                    increaseFont("List.font");
                } catch (Exception e) {}

                //Turn off metal's use of bold fonts
	        UIManager.put("swing.boldMetal", Boolean.FALSE);
					createAndShowGUI();

            }
        });
    }//end main
    
    
    /**
     * Displays the contents of the specified input stream as text.
     *
     * @param input
     *            The input stream to display as text.
     *
     * @throws IOException
     */
    private static void displayTextInputStream(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;

            System.out.println("    " + line);
        }
        System.out.println();
    }

    
}//end class dragdropfiles
