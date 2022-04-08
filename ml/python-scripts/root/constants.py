import os
from pathlib import Path

from sklearn.ensemble import RandomForestClassifier

from sklearn.preprocessing import MinMaxScaler

HOME = str(Path.home())

RESTEST_PATH    = HOME + '/RESTest'
RESTEST_RESULTS_PATH = RESTEST_PATH + '/target/test-data'

DEBUG_PATH =  HOME + '/Desktop/debug'

PREDICTOR = RandomForestClassifier(n_estimators=300, criterion='gini', class_weight='balanced_subsample')
SCALER = MinMaxScaler()

ALL_SERVICES = [
    "GitHub", 
    "Stripe_Coupons", 
    "Stripe_Products", 
    "Yelp_Businesses", 

    "YouTube_CommentsAndThreads", 
    "YouTube_Videos", 
    "YouTube_Search", 

    # "Foursquare", 
    # "Ohsome_GetElementsArea", 
    # "LanguageTool", 

    # "YouTube_Search_local", 
    # "Ohsome_GetElementsArea_mock", 
    # "LanguageTool_mod", 
]
