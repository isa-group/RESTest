import sys
import joblib
import numpy as np
import pandas as pd
import os

from root.constants import PREDICTOR, RESTEST_RESULTS_PATH, SCALER
from root.data.dataset import read_dataset
from root.data.processing import label_requests, read_raw, raw2preprocessed
from root.helpers.properties import PropertiesFile
from root.helpers.resampling import resample

if len(sys.argv) > 1:

    # path to the .properties file
    properties_file = sys.argv[1]

    # resampling ratio
    resampling_ratio = float(sys.argv[2])

    # number of tests
    n_tests = int(sys.argv[3])

else: # debug
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/GitHub/props.properties'
    resampling_ratio = 0.8
    n_tests = 5

# define the properties object
try:
    properties = PropertiesFile(properties_file)
except FileNotFoundError:
    raise Exception('Properties file '+ properties_file + 'does not exist.')

# path to the experiment folder
experiment_folder = RESTEST_RESULTS_PATH + '/' + properties.get('experiment.name')
target = experiment_folder + '/pool.csv'

# get pool data
try:
    pool = read_raw(target)
    X_pool = raw2preprocessed(pool, properties_file)
except FileNotFoundError:
    raise Exception("pool data '"+target+"' not found.")

# try to read training dataset, except random testing (should happen in first iteration)
try:
    train_data = read_dataset(experiment_folder, properties_file)

    # preprocess train data
    X_train = train_data.preprocess_requests()
    y_train = train_data.obt_validities

    # transform train/pool data to tree form
    X_pool = raw2preprocessed(pool, properties_file)

    # subselect the common features
    common_features = [c for c in X_train.columns if c in X_pool.columns]
    X_train = X_train[common_features]
    X_pool  = X_pool[common_features]

    # resample and compute certainty threshold
    X_train, y_train, certainty_threshold = resample(X_train, y_train, resampling_ratio)

    # scale data
    scaler  = SCALER
    X_train = scaler.fit_transform(X_train)
    X_pool  = scaler.transform(X_pool)

    # define and fit the predictor
    predictor = PREDICTOR
    predictor.fit(X_train, y_train)

    # label the pool with predictions
    predictions = pd.Series(predictor.predict(X_pool), index=pool.index)
    pool = label_requests(pool, predictions)

    # compute and sort predicted probabilities
    probabilities = pd.Series(np.max(predictor.predict_proba(X_pool), axis=1), index=pool.index).sort_values(ascending=True)

    # query first n_tests requests
    query = pool.loc[probabilities.index.tolist()[:n_tests]]

except FileNotFoundError:
    print('No training data found in ' + experiment_folder + '. Proceeding with random testing.')
    query = pool[:n_tests]

# write query requests
query.to_csv(target)

print(f"{len(query.index)} requests queried from the pool of {len(pool.index)}, and saved to "+ target + '.')
