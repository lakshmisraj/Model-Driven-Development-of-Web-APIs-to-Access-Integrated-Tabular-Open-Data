# Model-Driven-Development-of-Web-APIs-to-Access-Integrated-Tabular-Open-Data

To run the project :-

go to /similarity/table_similarity and execute the below command :-

```
python table_similarity.py
```

similarity.json file is created.

Move or copy the generated JSON file to the home directory 
and execute the below command :-

```
java -jar tableunion.jar similarity.json UNION/JOIN 0.75/0.95 api
```

Enter UNION if the operation to be performed is union and JOIN for join.
Set Threshold as 0.75 for UNION and 0.95 for JOIN

'api' argument is optional (To generate the API)


Required Python Libraries :-
1. numpy 1.15.1
2. scipy  1.1.0
3. strsimpy for JaroWinkler
4. gensim 3.8.0

