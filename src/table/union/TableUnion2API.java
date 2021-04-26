package table.union;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.ValueMeta;
import org.pentaho.di.trans.StepLoader;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransHopMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.csvinput.CsvInputMeta;
import org.pentaho.di.trans.steps.mergejoin.MergeJoinMeta;
import org.pentaho.di.trans.steps.sort.SortRowsMeta;
import org.pentaho.di.trans.steps.textfileinput.TextFileInputField;
import org.pentaho.di.trans.steps.textfileoutput.TextFileField;
import org.pentaho.di.trans.steps.textfileoutput.TextFileOutputMeta;

import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;

import com.google.gson.*;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

public class TableUnion2API {
    
    //threshold to decide if two columns are similar enough to perform UNION or JOIN operations
    public static float similarity_threshold_for_columns = 0.75f;

    public static String operationChosen = ""; //operation chosen by user (union or join)
    public static boolean generateAPI = false; //generate API after creating CSV integrated file
    public static String fileName = "similarity.json";  //name of similarity file
    
    public static String integratedFileName = "integrated_file"; //integrated filename
    public static String transformationFilename = "generated_transformation.ktr";//name of the transformation file
    public static char csvSeparator = ',';  //separator used in CSV files 
    public static String tablesFolder = ""; // Path to tables to integrate

    public static void main(String[] args) {

        if(args.length > 1) {
            fileName = args[0];
            if(args.length >= 2) {
            	operationChosen = args[1];
            } 
            if(args.length == 3) {
            	if(args[2].contains("api")) {
            		generateAPI = true; //generating api if user enters api as option
            	}
            }
            if(args.length == 4) {
            	// threshold for join is 0.95
            	if(args[3].contains("api")) {
            		generateAPI = true;
            	}
            	String thresholdInput = args[2].replaceAll(",", ".");
    	    	if(thresholdInput.length() > 0) {
    	    		float threshold = similarity_threshold_for_columns;
    	    		try {
    	    			threshold = Float.parseFloat(thresholdInput);
    	    		} catch(NumberFormatException e) {
    	    			e.printStackTrace();
    	    		}
    		    	if(threshold > 0 && threshold <= 1) {
    			    	similarity_threshold_for_columns = threshold;
    		    	} else {
    		    		System.out.println("The threshold must be between 0 and 1(default threshold (0.75)): " + similarity_threshold_for_columns);
    		    	}
    	    	}
            }
        }
        else {
            //if no arguments entered, asking them individually
        	userInput();
        }
        
        readSimilarityFileAndIntegrate();
        createAPIfromIntegratedCSV();
    }
    
    public static void userInput() {
    	Scanner scan = null;
    	try {
	    	scan = new Scanner(System.in);
	    	System.out.println("Enter the name of the similarity file (default " + fileName + "): ");//asking similarity filename
	    	String similarity_filename = scan.nextLine();
	    	if(similarity_filename.length() > 0) {
		    	fileName = similarity_filename;
	    	}
	    	
	    	System.out.println("Enter the operation to apply (join, union or empty for default operation): ");//Asking the operation as a string
	    	String operationInput = scan.nextLine();
	    	if(operationInput.length() > 0) {
	    		operationChosen = operationInput;
	    	}
	    	
	    	System.out.println("Enter the threshold of similarity (from 0 to 1, empty for default threshold " + similarity_threshold_for_columns + "): ");
	    	String thresholdInput = scan.nextLine().replaceAll(",", ".");
	    	if(thresholdInput.length() > 0) {
	    		float threshold = similarity_threshold_for_columns;
	    		try {
	    			threshold = Float.parseFloat(thresholdInput);
	    		} catch(NumberFormatException e) {
	    			e.printStackTrace();
	    		}
		    	if(threshold > 0 && threshold <= 1) {
			    	similarity_threshold_for_columns = threshold;
		    	} else {
		    		System.out.println("The threshold must be between 0 and 1 (default threshold is 0.75): " + similarity_threshold_for_columns);
		    	}
	    	}
	    	//asking, if api has to be genrated
	    	System.out.println("Generate API or only the integrated CSV file? ('y' for API, 'n' for creating CSV): ");
	    	String apiInput = scan.nextLine();
	    	if(apiInput.length() > 0 && apiInput.contains("y")) {
	    		generateAPI = true;
	    	}
    	} catch(Exception e) {
    		e.printStackTrace();
    	} finally {
    		scan.close();
    	}
    }
    
    public static void readSimilarityFileAndIntegrate() {
        
        try (FileReader reader = new FileReader(fileName))
        {           
            //Read JSON file
            JsonObject jsonObj = (JsonObject) JsonParser.parseReader(reader);

            for (String keyStr : jsonObj.keySet()) {
                String tableName = keyStr;
                JsonObject tableJson = jsonObj.getAsJsonObject(keyStr);
                for (String keyStr2 : tableJson.keySet()) {
                    String otherTableName = keyStr2;
                    JsonArray similarityArray = tableJson.getAsJsonArray(keyStr2);
                    
                    float similarity_mean = 0.0f;
                    float counter = 0.0f;
                    
                    ArrayList <String> tableColumnsUsed = new ArrayList<String> ();
                    ArrayList <String> otherTableColumnsUsed = new ArrayList<String> ();
                    HashMap<String,String> bestMatchesColumns = new HashMap<String, String> ();
                    
                    for(JsonElement columnsComparison : similarityArray) {
                        JsonArray columnsComparisonArray = columnsComparison.getAsJsonArray();
                        
                        if(columnsComparisonArray.size() == 3) {
                            float similarity_columns = columnsComparisonArray.get(2).getAsFloat();
                            String tableColumnName = columnsComparisonArray.get(0).getAsString();
                            String otherTableColumnName = columnsComparisonArray.get(1).getAsString();
                            
                            if(similarity_columns >= similarity_threshold_for_columns 
                                    && !tableColumnsUsed.contains(tableColumnName)
                                    && !otherTableColumnsUsed.contains(otherTableColumnName)) {
                                
                                similarity_mean += similarity_columns;
                                counter++;
                                tableColumnsUsed.add(tableColumnName);
                                otherTableColumnsUsed.add(otherTableColumnName);
                                bestMatchesColumns.put(tableColumnName, otherTableColumnName);
                            }
                        }
                    }
                    
                    if(counter > 0) {
                        // Conditions to choose between UNION or JOIN operations for tables
                        similarity_mean = similarity_mean / counter;
                        boolean union = true, join = false;
                        if(counter == 1 || operationChosen.toLowerCase().contentEquals("join")) {
                            join = true;
                            union = false;
                        }
                        dataIntegration(union, join, tableName, otherTableName, bestMatchesColumns);
                    }
                    
                }
            }

        } catch(Exception e) {
            System.out.print(e.getMessage());
        }
    }
    
    public static void dataIntegration (boolean union, boolean join, String tableName, String otherTableName, 
            HashMap<String,String> bestMatchesColumns) {
        String operation = "";
        if(union) {
            operation = "UNION";
        } else if (join) {
            operation = "JOIN";
        }
        System.out.println("");
        System.out.println("Integrating " + tableName + " " + operation + " " + otherTableName 
                + " by columns: " + bestMatchesColumns.keySet().toString() 
                + " with " +  bestMatchesColumns.values().toString());

        String csvFile1 = tableName;
        String csvFile2 = otherTableName; 
        
        CSVReader reader1 = null;
        CSVReader reader2 = null;
        String csvSplitBy = ",";

        FileInputStream fis1 = null;
        try {
            fis1 = new FileInputStream(csvFile1);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } 
        FilterInputStream filter1 = new BufferedInputStream(fis1); 
        BOMInputStream bomIn1 = new BOMInputStream(filter1, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE);
        
        try {
            if (bomIn1.hasBOM()) {
                System.out.println("has a UTF-8 BOM");
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        try {
            int firstNonBOMByte = bomIn1.read();
        } catch (IOException e1) {
            e1.printStackTrace();
        } //Skips BOM
                 
        // Auto detect CSV separator (, or ;)
        char csvSeparatorAux1 = csvSeparator, csvSeparatorAux2 = csvSeparator;
        String[] fields1 = null, fields2 = null;
        try
        {
            CsvParserSettings settings = new CsvParserSettings();
            settings.detectFormatAutomatically();
            CsvParser parserNew = new CsvParser(settings);
            parserNew.beginParsing(new File(csvFile1));
            fields1 = parserNew.parseNext();
            CsvFormat format = parserNew.getDetectedFormat();
            csvSeparatorAux1 = format.getDelimiter();
            parserNew.stopParsing();
        } catch(Exception e) {
            e.printStackTrace();
        }
        try {
            CsvParserSettings settings = new CsvParserSettings();
            settings.detectFormatAutomatically();           
            CsvParser parserNew2 = new CsvParser(settings);
            parserNew2.beginParsing(new File(csvFile2));
            fields2 = parserNew2.parseNext();
            CsvFormat format2 = parserNew2.getDetectedFormat();
            csvSeparatorAux2 = format2.getDelimiter();
            parserNew2.stopParsing();
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        try {
            
            CSVParser parser1 = new CSVParserBuilder().withSeparator(csvSeparatorAux1).build();
            CSVParser parser2 = new CSVParserBuilder().withSeparator(csvSeparatorAux2).build();
            reader1 = new CSVReaderBuilder(new InputStreamReader(new FileInputStream(csvFile1), "UTF8"))
                    .withCSVParser(parser1).build();
            reader2 = new CSVReaderBuilder(new InputStreamReader(new FileInputStream(csvFile2), "UTF8"))
                    .withCSVParser(parser2).build();

            integratedFileName = cleanString(tableName.replace(".", "") + otherTableName.replace(".", ""));
            if(join) {
                createTransformations(csvFile1, csvFile2, csvSeparatorAux1 + "", csvSeparatorAux2 + "", 
                        fields1, bestMatchesColumns.keySet(), fields2, bestMatchesColumns.values(), integratedFileName);
                runTransformations();
            } else {
            	performUnionOperation(reader1, reader2, bestMatchesColumns, tableName, otherTableName, csvFile1, csvFile2);
            }
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
    }
    
    private static void createTransformations
        (String csvFilename1, String csvFilename2, String separator1, String separator2,
                String[] fields1, Set<String> fields1keys, String[] fields2, Collection<String> fields2keys, 
                String integratedFileName) {

          try {
        	  StepLoader.init();
          } catch (KettleException e1) {
              e1.printStackTrace();
          }
          // create empty transformation definition
          TransMeta transMeta = new TransMeta();
          transMeta.setName("Join Transformation");

          // Create "CSV input" Step
          System.out.println( "Adding CSV input Step" );
          CsvInputMeta csvinput = new CsvInputMeta();

          /*******************************For Table 1*****************************************/
          csvinput.setFilename(csvFilename1);

          csvinput.setDelimiter(separator1);
          csvinput.setHeaderPresent(true);
          csvinput.setEnclosure("\"");
          csvinput.setBufferSize("50000");

          TextFileInputField[] inputFields1 = new TextFileInputField[ fields1.length ];
          int idx = 0;
          Set<String> fields1keysNotNormalized = new HashSet<String>();
          for (String fieldName : fields1) {
            TextFileInputField field = new TextFileInputField();
            field.setName(fieldName);
	      	  if(fields1keys.contains(normalize(fieldName))) {
	    		  fields1keysNotNormalized.add(fieldName);
	    	  }
            inputFields1[idx] = field;
            idx++;
          }
          csvinput.setInputFields(inputFields1);

          StepMeta csvinputStepMeta = new StepMeta("CsvInput", csvinput);
          // make sure the step appears on the canvas and is properly placed in spoon
          csvinputStepMeta.setDraw( true );
          csvinputStepMeta.setLocation(100, 100);

          // include step in transformation
          transMeta.addStep(csvinputStepMeta);
          
          // Add sort Step and connect it the previous step
          System.out.println( "Adding sort Step" );
          // Create Step Definition 
          SortRowsMeta sortMeta = new SortRowsMeta();

          String[] sortFields = null;
          sortFields = fields1keysNotNormalized.toArray(new String[fields1keysNotNormalized.size()]);
          
          boolean[] ascendingOrderFields = new boolean[sortFields.length];
          for(int i = 0; i < ascendingOrderFields.length; i++) {
              ascendingOrderFields[i] = true;
              
          }
          boolean[] caseSensitiveFields = new boolean[sortFields.length];
          for(int i = 0; i < caseSensitiveFields.length; i++) {
              caseSensitiveFields[i] = false;
              
          }
          boolean[] collatorEnabledFields = new boolean[sortFields.length];
          for(int i = 0; i < collatorEnabledFields.length; i++) {
              collatorEnabledFields[i] = false;
              
          }
          int[] collatorStrengthFields = new int[sortFields.length];
          for(int i = 0; i < collatorStrengthFields.length; i++) {
              collatorStrengthFields[i] = 0;
              
          }
          boolean[] preSortedFields = new boolean[sortFields.length];
          for(int i = 0; i < preSortedFields.length; i++) {
              preSortedFields[i] = false;
              
          }
          sortMeta.setFieldName(sortFields);
          sortMeta.setAscending(ascendingOrderFields);
          
          sortMeta.setDirectory("/tmp");
          sortMeta.setCaseSensitive(caseSensitiveFields);
          StepMeta sortStepMeta = new StepMeta("Sort", sortMeta);

          //making sure the step appears alright in spoon
          sortStepMeta.setDraw(true);
          sortStepMeta.setLocation(500, 100);

          // include step in transformation
          transMeta.addStep(sortStepMeta);

          // connect row generator to add sequence step
          transMeta.addTransHop(new TransHopMeta(csvinputStepMeta, sortStepMeta));

          // Create "CSV input" Step
          System.out.println( "Adding CSV input Step 2" );
          CsvInputMeta csvinput2 = new CsvInputMeta();
        /*******************************For Table 2*****************************************/
          csvinput2.setFilename(csvFilename2);

          csvinput2.setDelimiter(separator2);
          csvinput2.setHeaderPresent(true);
          csvinput2.setEnclosure("\"");
          csvinput2.setBufferSize("50000");

          TextFileInputField[] inputFields2 = new TextFileInputField[ fields2.length ];
          int idx2 = 0;
          Set<String> fields2keysNotNormalized = new HashSet<String>();        	  
          for (String fieldName2 : fields2) {
            TextFileInputField field2 = new TextFileInputField();
            field2.setName(fieldName2);
            if(fields2keys.contains(normalize(fieldName2))) {
      		  fields2keysNotNormalized.add(fieldName2);
      	  	}
            inputFields2[idx2] = field2;
            idx2++;
          }
          csvinput2.setInputFields(inputFields2);
          StepMeta csvinputStepMeta2 = new StepMeta("CsvInput2", csvinput2);
          // make sure the step appears on the canvas and is properly placed in spoon
          csvinputStepMeta2.setDraw( true );
          csvinputStepMeta2.setLocation(100, 200);

          // include step in transformation
          transMeta.addStep(csvinputStepMeta2);
          
          // Add sort Step and connect it the previous step
          System.out.println( "Adding sort Step 2" );
          // Create Step Definition 
          SortRowsMeta sortMeta2 = new SortRowsMeta();
          //String sortPluginId2 = registry.getPluginId(StepPluginType.class, sortMeta2);

          String[] sortFields2 = null;
          sortFields2 = fields2keysNotNormalized.toArray(new String[fields2keysNotNormalized.size()]);
          
          boolean[] ascendingOrderFields2 = new boolean[sortFields2.length];
          for(int i = 0; i < ascendingOrderFields2.length; i++) {
              ascendingOrderFields2[i] = true;
              
          }
          boolean[] caseSensitiveFields2 = new boolean[sortFields2.length];
          for(int i = 0; i < caseSensitiveFields2.length; i++) {
              caseSensitiveFields2[i] = false;
              
          }
          boolean[] collatorEnabledFields2 = new boolean[sortFields2.length];
          for(int i = 0; i < collatorEnabledFields2.length; i++) {
              collatorEnabledFields2[i] = false;
              
          }
          int[] collatorStrengthFields2 = new int[sortFields2.length];
          for(int i = 0; i < collatorStrengthFields2.length; i++) {
              collatorStrengthFields2[i] = 0;
              
          }
          boolean[] preSortedFields2 = new boolean[sortFields2.length];
          for(int i = 0; i < preSortedFields2.length; i++) {
              preSortedFields2[i] = false;
              
          }
          sortMeta2.setFieldName(sortFields2);
          sortMeta2.setAscending(ascendingOrderFields2);
          
          sortMeta2.setDirectory("/tmp");
          sortMeta2.setCaseSensitive(caseSensitiveFields2);

          StepMeta sortStepMeta2 = new StepMeta("Sort2", sortMeta2);

          //make sure the step appears alright in spoon
          sortStepMeta2.setDraw(true);
          sortStepMeta2.setLocation(500, 200);

          // include step in transformation
          transMeta.addStep(sortStepMeta2);

          // connect row generator to add sequence step
          transMeta.addTransHop(new TransHopMeta(csvinputStepMeta2, sortStepMeta2));

           
          // Add Merge Join Step and connect it previous steps
          System.out.println( "Adding merge Step" );
          //Create Step Definition 
          MergeJoinMeta mergeMeta = new MergeJoinMeta();
          //String mergePluginId = registry.getPluginId(StepPluginType.class, mergeMeta);
          mergeMeta.allocate(sortFields.length, sortFields2.length);
          mergeMeta.setKeyFields1(sortFields);
          mergeMeta.setKeyFields2(sortFields2);
          mergeMeta.setJoinType("FULL OUTER");
          mergeMeta.setStepMeta1(sortStepMeta);
          mergeMeta.setStepMeta2(sortStepMeta2);

          //StepMeta mergeStepMeta = new StepMeta(mergePluginId, "MergeJoin", mergeMeta);
          StepMeta mergeStepMeta = new StepMeta("MergeJoin", mergeMeta);

          // make sure the step appears alright in spoon
          mergeStepMeta.setDraw(true);
          mergeStepMeta.setLocation(700, 150);

          // include step in transformation
          transMeta.addStep(mergeStepMeta);

          // connect row generator to add sequence step
          transMeta.addTransHop(new TransHopMeta(sortStepMeta, mergeStepMeta));
          transMeta.addTransHop(new TransHopMeta(sortStepMeta2, mergeStepMeta));

           
          // Add Text Output Step and connect it previous steps
          System.out.println( "Adding text output Step" );
          // Create Step Definition 
          TextFileOutputMeta textFileOutputMeta = new TextFileOutputMeta();
          
          textFileOutputMeta.setFileName(integratedFileName);          
          textFileOutputMeta.setExtension("csv");
          textFileOutputMeta.setEnclosure("\"");
          textFileOutputMeta.setSeparator(",");
          textFileOutputMeta.setHeaderEnabled(true);
          /*Table Integration Done*/

          TextFileField[] textFields = new TextFileField[fields1.length + fields2.length - fields2keys.size()];
          int idy = 0;
          for (String fieldName : fields1) {
            TextFileField field = new TextFileField();
            field.setName(fieldName);
            field.setType("String");
            field.setTrimType(ValueMeta.TRIM_TYPE_BOTH);
            field.setLength(-1);
            field.setPrecision(-1);

            textFields[idy] = field;
            idy++;
          }
          for (String fieldName2 : fields2) {
        	// Avoid repeating keys
        	if(!fields2keys.contains(normalize(fieldName2))) {
	            TextFileField field2 = new TextFileField();
	            field2.setName(fieldName2);
	            field2.setType("String");
	            field2.setTrimType(ValueMeta.TRIM_TYPE_BOTH);
	            field2.setLength(-1);
	            field2.setPrecision(-1);
	
	            if(idy < textFields.length) {
	            	textFields[idy] = field2;
		            idy++;  
	            }  		
        	}
          }
          
          textFileOutputMeta.setOutputFields(textFields);
          StepMeta textOutputStepMeta = new StepMeta("TextOutput", textFileOutputMeta);

          // make sure the step appears alright in spoon
          textOutputStepMeta.setDraw(true);
          textOutputStepMeta.setLocation(900, 150);

          // include step in transformation
          transMeta.addStep(textOutputStepMeta);

          // connect row generator to add sequence step
          transMeta.addTransHop(new TransHopMeta(mergeStepMeta, textOutputStepMeta));

          
          
          String outputFilename = transformationFilename;
          System.out.println( "Saving to " + outputFilename );
          String xml = null;
          try {
              xml = transMeta.getXML();
          } catch (Exception e) {
              //TODO Auto-generated catch block
              e.printStackTrace();
          }
          File file = new File(outputFilename);
          try {
              FileUtils.writeStringToFile(file, xml, "UTF-8");
          } catch (IOException e) {
              //TODO Auto-generated catch block
              e.printStackTrace();
          }
    }
    
    private static void runTransformations() {
        TransMeta metaData = null;
        try {
            metaData = new TransMeta(transformationFilename);
        } catch (KettleXMLException e) {
            //TODO Auto-generated catch block
            e.printStackTrace();
        }

        Trans transformation = new Trans(metaData);
        try {
            transformation.execute(null);
        } catch (KettleException e) {
            //TODO Auto-generated catch block
            e.printStackTrace();
        }
        transformation.waitUntilFinished();
    }
    
    private static void performUnionOperation(CSVReader reader1, CSVReader reader2,
    		HashMap<String,String> bestMatchesColumns, String tableName, String otherTableName,
    		String csvFile1, String csvFile2) {
        FileWriter writer = null;

        String[] file1 = null;
        String[] file2 = null;
        String line1 = "";
        String line2= "";
        
        ArrayList<Integer> table1columnPositions = new ArrayList<>();
        ArrayList<Integer> table1otherColumnPositions = new ArrayList<>();
        ArrayList<Integer> table2columnPositions = new ArrayList<>();
        ArrayList<Integer> table2otherColumnPositions = new ArrayList<>();
    	try {
	    	writer = new FileWriter(integratedFileName + ".csv");
	        
	        int counter = 0;  
	        while ((file1 = reader1.readNext()) != null) {
	            line1 = "";
	            if(counter == 0) {
	                file2 = reader2.readNext();
	                for (Entry<String, String> entry : bestMatchesColumns.entrySet()) {
	                    String key = entry.getKey();
	                    String value = entry.getValue();
	                    for(int i = 0; i < file1.length; i++) {
	                        file1[i] = normalize(file1[i]);
	                        if(key.contentEquals(file1[i])) {
	                            table1columnPositions.add(i);
	                        } else {
	                            if(!table1otherColumnPositions.contains(i)) {
	                                table1otherColumnPositions.add(i);
	                            }
	                        }
	                    }
	                    for(int i = 0; i < file2.length; i++) {
	                        file2[i] = normalize(file2[i]);
	                        if(value.contentEquals(file2[i])) {
	                            table2columnPositions.add(i);
	                        } else {
	                            if(!table2otherColumnPositions.contains(i)) {
	                                table2otherColumnPositions.add(i);
	                            }
	                        }
	                    }
	                }
	                for(int resultsIndex = 0; (resultsIndex < table1columnPositions.size())
	                        && (resultsIndex < table2columnPositions.size()); resultsIndex++) {
	                    //TODO: avoid repeated words
	                	if(cleanString(file1[table1columnPositions.get(resultsIndex)]).replaceAll(",", ".") == 
	                			cleanString(file2[table2columnPositions.get(resultsIndex)]).replaceAll(",", ".")) {
	                		line1 += cleanString(file1[table1columnPositions.get(resultsIndex)]).replaceAll(",", ".");
	                	} else {
	                    line1 += cleanString(file1[table1columnPositions.get(resultsIndex)]).replaceAll(",", ".")
	                            + "_"
	                            + cleanString(file2[table2columnPositions.get(resultsIndex)]).replaceAll(",", ".") 
	                            + ",";
	                	}
	                }
	                if(line1.length() > 0) {
	                    writer.write(line1.substring(0, line1.length() - 1));                   
	                }
	            } else {
	                for(int i : table1columnPositions) {
	                    if(i < file1.length) {
	                        line1 += cleanStringInvalidChars(file1[i].replaceAll(",", ".")) + ",";
	                    }
	                }
	                if(line1.length() > 0) {
	                    writer.write(System.lineSeparator());
	                    writer.write(line1.substring(0, line1.length() - 1));
	                }
	            }
	            counter++;
	        }
		}
		catch (IOException e)
		{
		    e.printStackTrace();
		} catch (CsvValidationException e) {
		    e.printStackTrace();
		}
		finally
		{
		    try
		    {
		        reader1.close();
		    } 
		    catch (IOException e) 
		    {
		        e.printStackTrace();
		    }
		}
		
		System.out.println("Read table: " + tableName);
		
		
		FileInputStream fis2 = null;
		try {
		    fis2 = new FileInputStream(csvFile2);
		} catch (FileNotFoundException e1) {
		    e1.printStackTrace();
		} 
		FilterInputStream filter2 = new BufferedInputStream(fis2); 
		BOMInputStream bomIn2 = new BOMInputStream(filter2, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE,
		        ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE);
		
		try {
		    if (bomIn2.hasBOM()) {
		        // has a UTF-8 BOM
		        System.out.println("has a UTF-8 BOM");
		    }
		} catch (IOException e1) {
		    e1.printStackTrace();
		}
		try {
		    int firstNonBOMByte = bomIn2.read();
		} catch (IOException e1) {
		    e1.printStackTrace();
		} // Skips BOM
		 
		try
		{    
		    while ((file2 = reader2.readNext()) != null) {
		        line2 = "";
		        for(int i : table2columnPositions) {
		            if(i < file2.length) {
		                line2 += cleanStringInvalidChars(file2[i].replaceAll(",", ".")) + ",";
		            }
		        }
		        if(line2.length() > 0) {
		            writer.write(System.lineSeparator());
		            writer.write(line2.substring(0, line2.length() - 1));
		        }
		    }
		}
		catch (IOException e)
		{
		    e.printStackTrace();
		} catch (CsvValidationException e) {
		    e.printStackTrace();
		}
		finally
		{
		    try
		    {
		        reader2.close();
		        writer.close();
		    } 
		    catch (IOException e) 
		    {
		        e.printStackTrace();
		    }
		}
		
		System.out.println("Read table: " + otherTableName);        
		System.out.println("Written new table");
    }
    
    private static void createAPIfromIntegratedCSV() {
    	if(generateAPI) {
    		fAG.AG.main(new String[] {"csv2api", integratedFileName});
    		try {
    			InputStream src = TableUnion2API.class.getResourceAsStream("/ag.jar");
    			File f = new File("ag.jar");
    			if(!f.exists()) {
    				Files.copy(src, Paths.get("ag.jar"));
    			}
    		} catch (Exception e) {
            	System.out.println("Error creating configuration files");
            	e.printStackTrace();
    		}
    		
    		try {
            	System.out.println("Generating API...");
    			Process ps=Runtime.getRuntime().exec(
    					new String[]{"cmd","/c","start","cmd","/k","java","-jar","ag.jar","csv2api",integratedFileName});
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}    	
    }
    
    private static String normalize(String s) {
        // Split camelCased-words
        String res = "";
        if(s != null && s.length()>0) {
	        for (String w : s.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")) {
	            res += w + " ";
	        }
	        
	        //Split words including '-', '_' and '/', remove punctuation and extra spaces, and lowercase
	        res = res.replaceAll("[-_/!\"#$%&'()*+, -./:;<=>?@\\[\\]\\^_`{|}~]", " ") 
	                .replaceAll("( +)"," ").trim().toLowerCase();
            
        }
        return res;
    }
    
    private static String cleanString(String s) {
        s = s.trim();
        s = StringUtils.stripAccents(s.replaceAll("\u00f1", "ny").replaceAll(" ", " ").replaceAll("/", "_").replaceAll("\"", "").replaceAll("\'", "")
            .replaceAll("\\?", "").replaceAll("\\+", "plus").replaceAll("\\(", "_").replaceAll("\\)", "_")
            .replaceAll("\\[", "_").replaceAll("\\]", "_").replaceAll("\\{", "_").replaceAll("\\}", "_"))
            .replaceAll("\\P{Print}", "");
        return s;
    }
    
    private static String cleanStringInvalidChars(String s) {
        return StringUtils.stripAccents(s.replaceAll("\u00f1", "ny").replaceAll("\"", "").replaceAll("\'", ""))
                .replaceAll("\\P{Print}", "").trim();
    }

}
