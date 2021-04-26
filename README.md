# Model-Driven-Development-of-Web-APIs-to-Access-Integrated-Tabular-Open-Data

Required Python Libraries:
1. numpy 1.15.1
2. scipy  1.1.0
3. strsimpy for JaroWinkler
4. gensim 3.8.0

to run:
go to /similarity/table_similarity and execute

```
python table_similarity.py
```

similarity.json file is created.
Move or copy the JSON file into home directory 
and run 

```
java -jar tableunion.jar similarity.json UNION/JOIN 0.75/0.95 api
```

Enter UNION if the operation to be performed is union and JOIN for join.
Threshold as 0.75 if Union and 0.95 if JOIN

api is optional (it is to generate the api)
