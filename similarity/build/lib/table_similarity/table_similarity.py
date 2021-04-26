import os
import re
import string
import csv
import json
from collections import defaultdict
#import Levenshtein as lev  # Calculate Levenshtein distance
from strsimpy.jaro_winkler import JaroWinkler
from scipy import spatial  # Cosine similarity between vectors
#genism
import gensim
from gensim.models import Word2Vec  # Manage word embeddings
from gensim.models import KeyedVectors  # Manage fastText pretrained word embeddings
from operator import itemgetter  # Sort dictionaries by value
#numpy
import numpy as np
import configparser  #for reading the configuration file

#Taking a column name and split camelCased-words, remove punctuation, extra whitespaces, lowercase, etc.
def normalize(col_name):
    #column_name: column name to normalize
    #return: normalized column name 
    split_words = re.sub('([A-Z][a-z]+)', r' \1', re.sub('([A-Z]+)', r' \1', col_name)).split()
    col_name = ' '.join(split_words)  #Split camelCased-words
    col_name = col_name.translate(str.maketrans('_-/', '   ')) #Split words including '-', '_' and '/'
    col_name = col_name.translate(str.maketrans(string.punctuation, ' '*len(string.punctuation)))#Remove punctuation
    col_name = ' '.join(col_name.split())#Remove extra whitespaces
    col_name = col_name.lower()  #converting it into Lowercase

    return col_name

 #create a dictionary fro every csv file with table names as keys, 
    #which contain a dictionary with column names as keys and list of values as content
def load_tables(tables_dir):
    #tables_dir= directory storing the tables in CSV format, with header, delimiter ',' and quotechar '"'
    #return: a dictionary with all the content of the tables
    dict_tables = defaultdict()
    for file_name in os.listdir(tables_dir):
        with open(os.path.join(tables_dir, file_name)) as input_file:
            csv_reader = csv.DictReader(input_file, delimiter = ',', quotechar = '"')
            dict_tables[file_name] = defaultdict(list)
            for row in csv_reader:
                for column_name in row:
                    dict_tables[file_name][normalize(column_name)].append(normalize(row[column_name]))

    return dict_tables

#Given the values of a column, compute the mean vector of the word embeddings of each value
#model_contents: word embedding model to obtain the vector for each value
def get_mean_vector(model_contents, values):
    #values: list of values of a column
    #return: an embedding vector representing the mean of all the values in a column
    list_tokens = []
    for token in values:
        list_tokens.extend(token.split()) #Extract tokens from attributes with multiple words
    #Remove out-of-vocabulary words after normalization
    values = [normalize(x) for x in list_tokens if normalize(x) in model_contents]  
    if len(values) >= 1:
        return np.mean(model_contents[values], axis = 0)
    else:
        return []

#Creates a dictionary where every column is represented by an embedding vector
def create_vectors_content(model_contents, dict_tables):
    #dict_tables: dictionary that contains all the values of the columns for each table
    #model_contents: word embedding model to calculate the vector of the column values
    #return: a dictionary that stores, for every table in the directory, an embedding vector for every column#
    vectors_content = defaultdict()
    for table in dict_tables:
        vectors_content[table] = defaultdict()
        for column in dict_tables[table]:
            vectors_content[table][column] = get_mean_vector(model_contents, dict_tables[table][column])

    return vectors_content

#Calculates the similarity between two columns
def calculate_similarity(model_names, vectors_content, alpha, table_name1, table_name2, col_name1, col_name2):
    #model_names: word embedding model to compare the names of the columns
    #vectors_content: word embedding vectors of the columns content
    #alpha: specifies the weight in the final formula of the names model and the content model
    #table_name1, table_name2: name sof the tables to be compared
    #col_name1, col_name2: names of column of first and table table being compared
    #return: float value indicating the degree of similarity in the interval [0, 1]

    # Keep the words from the column name in the vocabulary of the names model_names
    tokens_1 = [token for token in col_name1.split() if token in model_names]
    tokens_2 = [token for token in col_name2.split() if token in model_names]

    # Similarity betweeen columns names. If there is no coverage for the columns names in the model_names, 
    # perform JaroWrinkler as a backup 
    jarowinkler = JaroWinkler()
    if tokens_1 and tokens_2:
       similarity_names = model_names.wv.n_similarity(tokens_1, tokens_2)  #Computes the average of the vectors and then the cosine similarity
    else:
       similarity_names = jarowinkler.similarity(col_name1, col_name2) #Computing similarity using JaroWrinkler 
        

    # Similarity between columns content. If there is no vectors specified for the content, keep only the similarity of names
    if len(vectors_content[table_name1][col_name1]) == 300 and len(vectors_content[table_name2][col_name2]) == 300:
        # Calculate the cosine similarity between two word embeddings vectors
        similarity_content = 1 - spatial.distance.cosine(vectors_content[table_name1][col_name1], vectors_content[table_name2][col_name2])
        similarity = alpha * similarity_names + (1 - alpha) * similarity_content
    else:
        similarity = similarity_names

    return float(similarity)  #To avoid problems with float32 values that are not JSON serializable

#Loads in memory the word embeddings models passed as parameter
def load_models(model_names_cfg, model_contents_cfg, list_models_files):
    # model_names_cfg: name of the model for the names of the columns
    # model_contents_cfg: name of the model for the contents of the cells
    #returns: the two models loaded

    if model_names_cfg == 'wikitables_names':
        # WikiTables task-specific Word2Vec model (based on names of the columns)
        model_names = Word2Vec.load(list_models_files[0])
    elif model_names_cfg == 'wikitables_contents':
        # WikiTables task-specific Word2Vec model (based on contents of the cells)
        model_names = Word2Vec.load(list_models_files[1])
    elif model_names_cfg == 'fasttext':
        # ftw = fastText word model
        model_names = KeyedVectors.load_word2vec_format(list_models_files[2])
    else: 
        model_names = KeyedVectors.load_word2vec_format(list_models_files[3], binary=True)

    #If the model is the same in both cases, just equal variables
    if model_names_cfg == model_contents_cfg:
        model_contents = model_names
    elif model_contents_cfg == 'wikitables_names':
        model_contents = Word2Vec.load(list_models_files[0])
    elif model_contents_cfg == 'wikitables_contents':
        model_contents = Word2Vec.load(list_models_files[1])
    elif model_contents_cfg == 'fasttext':
        model_contents = KeyedVectors.load_word2vec_format(list_models_files[2])
    else:
        model_contents = KeyedVectors.load_word2vec_format(list_models_files[3], binary=True)

    return model_names, model_contents

if __name__ == '__main__':
    # Load configuration from file
    list_models_files = []
    config = configparser.ConfigParser()

    config.read('config.ini')

    model_names_cfg = config['Setup']['model_names']
    model_contents_cfg = config['Setup']['model_contents']
    alpha = float(config['Setup']['alpha'])
    models_dir = config['Directories']['models']
    tables_dir = config['Directories']['tables']
    output_file = config['Files']['output']
    list_models_files.append(os.path.join(models_dir,config['Models']['wikitables_names']))
    list_models_files.append(os.path.join(models_dir,config['Models']['wikitables_contents']))
    list_models_files.append(os.path.join(models_dir,config['Models']['fasttext']))
    list_models_files.append(os.path.join(models_dir,config['Models']['google']))

    print('Parameters:-')
    print('Model names: ' + model_names_cfg)
    print('Model contents: ' + model_contents_cfg)
    print('Alpha: ' + str(alpha))

    print('Loading models- ')
    model_names, model_contents = load_models(model_names_cfg, model_contents_cfg, list_models_files)
    print('Done')

    print('Reading tables- ')
    dict_tables = load_tables(tables_dir)
    print('Done')

    print('Creating word embeddings of the content- ')
    vectors_content = create_vectors_content(model_contents, dict_tables)
    print('Done')

    dict_results = {}
    for table_name1 in dict_tables:
        table_similarity = defaultdict()
        for table_name2 in dict_tables:
            if table_name1 != table_name2 and (table_name2 not in dict_results or table_name1 not in dict_results[table_name2]):  
                table_similarity[table_name2] = []
                for col_name1 in dict_tables[table_name1]:
                    columns_similarity = {}
                    similarity = 0
                    for col_name2 in dict_tables[table_name2]:
                        similarity = calculate_similarity(model_names, vectors_content, alpha, table_name1, table_name2, col_name1, col_name2)
                        table_similarity[table_name2].append((col_name1, col_name2, similarity))
                    table_similarity[table_name2] = sorted(table_similarity[table_name2], key = itemgetter(2), reverse = True)
        if table_similarity:
            dict_results[table_name1] = table_similarity

    print('Saving to file"' + output_file + '"... -')
    with open(output_file, 'w') as file:
        json.dump(dict_results, file)
    print('Done')
