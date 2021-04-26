'use strict';
var fs = require('fs');
var papa = require('papaparse');
var readline = require('readline');
var stream = require('stream');

exports.getOperation = function(args, res, next) {
  
  var examples = {};
  var fileName = "./data.csv";
  var result;
  var resultVisualisation;
  var jsonResult = new Object();
  var limit = 10000; //by default
  var offset = 0;
  var labels = [];
  var datasets = [];
  var allDatasets = [];
  var firstLine = "";
  var lineCount = 0;
  var rowNumber = 0;


  if (fs.existsSync(fileName)) {
    console.log('file exists');
    jsonResult.results = [];

    // Checking arguments
    /*var argsUndefined = false;

    if(args.apiPath === "/"){
      argsUndefined = true;
    }

    args = args.params;*/

    var filters = false;
    var visualisation = false;

    for(var i = 0; i < Object.keys(args).length; i++){
      if (Object.keys(args)[i] === "limit" || Object.keys(args)[i] === "offset"|| Object.keys(args)[i] === "visualisation"){
        if (Object.keys(args)[i] === "limit" && args[Object.keys(args)[i]].value != undefined){
          limit = parseInt(args[Object.keys(args)[i]].value);
        } 
        else if (Object.keys(args)[i] === "offset" && args[Object.keys(args)[i]].value != undefined){
          offset = parseInt(args[Object.keys(args)[i]].value);
        }
        else if (Object.keys(args)[i] === "visualisation" && args[Object.keys(args)[i]].value != undefined){
          visualisation = true;
        }
      }
      /*else if(args[Object.keys(args)[i]].value != undefined){
        argsUndefined = false;
      }*/
    } 

    var instream = fs.createReadStream(fileName);
    var outstream = new stream();
    var rl = readline.createInterface(instream, outstream);

    rl.on('line', function(data){      


      if(lineCount == 0){
        //firstLine = cleanString(data) + "\n";
        
        var dataSplit = data.split(",")
        for(var columnIndex = 0; columnIndex < dataSplit.length; columnIndex++){
          if(columnIndex < dataSplit.length - 1){
            firstLine += cleanString(dataSplit[columnIndex]) + ",";
          } else {
            firstLine += cleanString(dataSplit[columnIndex]) + "\n";
          }
        }
        //console.log("firstLine " + firstLine)
      } 
      else if(rowNumber < limit + offset) {
        var dataToParse = firstLine;  
        var dataSplit = data.split(",")
        for(var columnIndex = 0; columnIndex < dataSplit.length; columnIndex++){
          if(columnIndex < dataSplit.length - 1){
            dataToParse += cleanSpaces(dataSplit[columnIndex]) + ",";
          } else {
            dataToParse += cleanSpaces(dataSplit[columnIndex]);
          }
        }    
        //dataToParse = firstLine + data;

        // From csv to json
        papa.parse(dataToParse, 
          { 
            header: true,
            step: function(row) {

              result = row.data[0];
              //console.log("result row " + JSON.stringify(result));
                
              /*if(argsUndefined){
                if(rowNumber >= offset){
                  jsonResult.results = jsonResult.results.concat(result);
                }
                rowNumber++;
                if(visualisation){
                  labels.push("'" + result[Object.keys(result)[0]] + "'");
                  datasets = resultToDataset(visualisation, result, datasets);
                  allDatasets = resultToAllDataset(visualisation, result, allDatasets);
                }
              }
              else {*/
                var resultValidator = true;
                for(var j = 0; j < Object.keys(args).length; j++){
                  if(args[Object.keys(args)[j]].value != undefined){
                    if(Object.keys(args)[j] === Object.keys(result)[Object.keys(result).indexOf(Object.keys(args)[j])]){
                      if(result[Object.keys(result)[Object.keys(result).indexOf(Object.keys(args)[j])]] === args[Object.keys(args)[j]].value + ""){
                        resultValidator = true && resultValidator;
                      }
                      else if(cleanStringInvalidChars(result[Object.keys(result)[Object.keys(result).indexOf(Object.keys(args)[j])]]) === args[Object.keys(args)[j]].value + ""){
                        resultValidator = true && resultValidator;
                      }
                      else if(args[Object.keys(args)[j]].value + "" === "all" && result[Object.keys(result)[Object.keys(result).indexOf(Object.keys(args)[j])]].replace(/\s+/g, '') != ""){
                        resultValidator = true && resultValidator;
                      }
                      else {
                        resultValidator = false && resultValidator;
                      }
                    }
                  } 
                }

                if(resultValidator) {
                  if(rowNumber >= offset){
                    jsonResult.results = jsonResult.results.concat(result);
                  }
                  rowNumber++;
                  if(visualisation){
                    labels.push("'" + result[Object.keys(result)[0]] + "'");
                    datasets = resultToDataset(visualisation, result, datasets);
                    allDatasets = resultToAllDataset(visualisation, result, allDatasets);
                  }
                }
              /*}*/

            },
            complete: function(){
              
            },
            error: function(err){
              console.log(err);
              res.end();
            }       
          }
        );
      }

      lineCount++;

    });


    rl.on('close', function(){
      console.log('ReturnJSONFile'); 

      lineCount--;
      rowNumber-=offset;
      jsonResult.limit = limit;
      jsonResult.offset = offset;
      jsonResult.visualisation = visualisation;  
      jsonResult.fileSize = lineCount + " records";  
      jsonResult.resultsSize = rowNumber + " records";  

      //result = result.data;
      try{
        // Creating json object
        //result = "{ \"results\": " + JSON.stringify(result) + " }";

        console.log("Completed!");
        examples['application/json'] = jsonResult;
      } catch (err) {
          console.log(err);
      }

      if(Object.keys(examples).length > 0) {
        if(visualisation){
          res.setHeader('Content-Type' , 'text/html');
          var visualisationHtmlFile = readTextFile("./controllers/visualisation.html");
          if (typeof labels !== 'undefined' && labels !== null && labels.length > 0){
            visualisationHtmlFile = visualisationHtmlFile.replace("labelsLineChart", labels.join());
            visualisationHtmlFile = visualisationHtmlFile.replace("labelsBarChart", labels.join());
          }

          //console.log(datasets.toString());

          if (typeof datasets !== 'undefined' && datasets !== null && datasets.length > 0){

            var iteratorLineChart = 0;
            while(visualisationHtmlFile.indexOf("dataLineChart") >= 0) {
              visualisationHtmlFile = visualisationHtmlFile.replace("dataLineChart", datasets[iteratorLineChart].join());
              iteratorLineChart++;
            }
            var iteratorBarChart = 0;
            while(visualisationHtmlFile.indexOf("dataBarChart") >= 0) {
              visualisationHtmlFile = visualisationHtmlFile.replace("dataBarChart", datasets[iteratorBarChart].join());
              iteratorBarChart++;
            }

            if (typeof allDatasets !== 'undefined' && allDatasets !== null && allDatasets.length > 0){
              var iteratorPieChart = 0;
              while(visualisationHtmlFile.indexOf("dataPieChart") >= 0) {
                var differentValues = 0;
                let unique = [...new Set(allDatasets[iteratorPieChart])]; 
                differentValues = unique.length

                if(differentValues <= 6 && differentValues > 0) {
                  var arr = allDatasets[iteratorPieChart];
                  var result = pieAux(arr);

                  visualisationHtmlFile = visualisationHtmlFile.replace("dataPieChart", result[1].join());
                  visualisationHtmlFile = visualisationHtmlFile.replace("labelsPieChart", result[0].join());
                }else {
                  visualisationHtmlFile = visualisationHtmlFile.replace("dataPieChart", "'1'");
                  visualisationHtmlFile = visualisationHtmlFile.replace("labelsPieChart", "'data not classifiable'");
                }
                iteratorPieChart++;
              }
            }

          }

          writeTextFile("./controllers/visualisationGenerated.html", visualisationHtmlFile);
          res.write(visualisationHtmlFile);
          res.end();
        } else {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify(examples[Object.keys(examples)[0]] || {}, null, 2));
        }
      }
      else {
        res.end();
      }
    });
  } 
  else {
        res.end();
  }
}

function pieAux(arr) {
    var a = [], b = [], prev;

    arr.sort();
    for ( var i = 0; i < arr.length; i++ ) {
        if ( arr[i] !== prev ) {
            a.push(arr[i]);
            b.push(1);
        } else {
            b[b.length-1]++;
        }
        prev = arr[i];
    }

    return [a, b];
}

function resultToDataset(visualisation, result, datasets)
{
  // Check columns types for visualisation
  var datasetsInserted = 0;
  for(var columnIt = 0; columnIt < Object.keys(result).length; columnIt++){
    if(parseInt(result[Object.keys(result)[columnIt]]) == result[Object.keys(result)[columnIt]]) {
      //console.log("data is an integer");
        // data is an integer
        datasetsInserted +=1;
        if(datasets.length < datasetsInserted){
          var dataset = [];
          dataset.push("'" + result[Object.keys(result)[columnIt]] + "'");
          datasets.push(dataset);
        } else {
          datasets[datasetsInserted - 1].push("'" +result[Object.keys(result)[columnIt]] + "'");
        }
    }
  }

  return datasets;
}

function resultToAllDataset(visualisation, result, allDatasets)
{
  // Check columns types for visualisation
  var datasetsInserted = 0;
  for(var columnIt = 0; columnIt < Object.keys(result).length; columnIt++){
      datasetsInserted +=1;
      if(allDatasets.length < datasetsInserted){
        var dataset = [];
        dataset.push("'" + result[Object.keys(result)[columnIt]] + "'");
        allDatasets.push(dataset);
      } else {
        allDatasets[datasetsInserted - 1].push("'" +result[Object.keys(result)[columnIt]] + "'");
      }
  }

  return allDatasets;
}

function readTextFile(file)
{
  var fs = require('fs');
 
  try {  
    var data = fs.readFileSync(file, 'utf8');
    return(data.toString());    
  } catch(e) {
    console.log('Error:', e.stack);
  }
  return("error reading file");
}

function writeTextFile(file, content)
{
  var fs = require('fs');
 
  try {  
    fs.writeFileSync(file, content, 'utf8'); 
  } catch(e) {
    console.log('Error:', e.stack);
  }
}

function cleanStringInvalidChars(s) {
  s = s.split("\u00f1").join("ny");
  s = s.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  s = s.split("\"").join("");
  s = s.split("\'").join("");
  s = s.split("\\P{Print}").join("");
  return s;
}

function cleanSpaces(s) {
  s = s.trim();
  if(s.length > 0 && s.charAt(0) === " "){
    s = s.substr(1);
  }
  return s;
}

function cleanString(s) {
  s = s.split("\u00f1").join("ny");
  s = s.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  s = s.trim();
  s = s.split("/").join("");
  s = s.split("\"").join("");
  s = s.split("\'").join("");
  s = s.split("\\?").join("");
  s = s.split("\\+").join("plus");
  s = s.split("\\-").join("minus");
  s = s.split("\\(").join("_");
  s = s.split("\\)").join("_");
  s = s.split("\\[").join("_");
  s = s.split("\\]").join("_");
  s = s.split("\\{").join("_");
  s = s.split("\\}").join("_");
  s = s.split("\\P{Print}").join("");
  s = s.split(" ").join("_");
  if(s.length > 0 && s.charAt(0) === "_"){
    s = s.substr(1);
  }
  return s;
}


exports.getexplained_by_freedom_to_make_life_choices_negative_affect = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getexplained_by_social_support_positive_affect = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getfreedom_to_make_life_choices_freedom_to_make_life_choices = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getgenerosity_generosity = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.gethealthy_life_expectancy_healthy_life_expectancy_at_birth = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getladder_score_life_ladder = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getlogged_gdp_per_capita_log_gdp_per_capita = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getperceptions_of_corruption_perceptions_of_corruption = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getsocial_support_social_support = function(args, res, next) {
	exports.getOperation(args, res, next); 
}
exports.getvisualisation = function(args, res, next) {
	var obj = new Object();obj.value = "visualisation";args.visualisation = obj;
	exports.getOperation(args, res, next); 
}
exports.getworldminushappinessminusreportminus2021csvworldminushappinessminusreportcsv = function(args, res, next) {
	exports.getOperation(args, res, next); 
}

