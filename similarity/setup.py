from setuptools import setup

setup(name = 'table_similarity',
    version = '0.1',
    description = 'calculating similarity between CSV tables.',
    author = '',
    author_email = '',
    packages = ['table_similarity'],
    install_requires = ['numpy>=1.15.1', 'strsimpy>=0.2.0', 'scipy>=1.1.0', 'gensim>=3.8.0'])
