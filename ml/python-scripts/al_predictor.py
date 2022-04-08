import sys
import joblib
import numpy as np
import pandas as pd

from root.constants import RESTEST_RESULTS_PATH
from root.processing.helpers import label_requests, read_raw, raw2preprocessed
from root.helpers.properties import PropertiesFile

if len(sys.argv) > 1:

    # path to the .properties file
    properties_file = sys.argv[1]

    # number of tests to query from pool
    n_tests = int(sys.argv[2])

else: # debug
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/GitHub/props.properties'
    n_tests = 5

# define the properties object
try:
    properties = PropertiesFile(properties_file)
except FileNotFoundError:
    raise Exception('Properties file '+ properties_file + 'does not exist.')

# path where to label test cases
experiment_folder = RESTEST_RESULTS_PATH + '/' + properties.get('experiment.name')
target = experiment_folder + '/pool.csv'

# path to the predictor
predictor_path = experiment_folder + '/predictor.joblib'

print("Executing al predictor...")
print('Predictor model path:        ' + predictor_path)
print('Target requests:             ' + target)
print('Number of requests to query: ' + str(n_tests))

# get pool data
try:
    pool = read_raw(target)
except FileNotFoundError:
    raise Exception("pool data '"+target+"' not found.")

# transform train/pool data to tree form
X_pool = raw2preprocessed(pool, spec)

# load predictor
predictor_path = experiment_folder + '/predictor.joblib'
try:
    predictor = joblib.load(experiment_folder + '/predictor.joblib')

    # subselect common features
    common_features = [f for f in predictor.features_names if f in X_pool.columns]
    X_pool = X_pool[common_features]

    # scale data
    scaler = predictor.scaler
    X_pool = scaler.transform(X_pool)

    # label the pool with predictions
    predictions = pd.Series(predictor.predict(X_pool), index=pool.index)
    pool = label_requests(pool, predictions)

    # compute and sort predicted probabilities
    probabilities = pd.Series(np.max(predictor.predict_proba(X_pool), axis=1), index=pool.index).sort_values(ascending=True)

    # query first n_tests requests
    query = pool.iloc[probabilities.index.tolist()[:n_tests]]

except Exception as e:
    print('No predictor found in ' + predictor_path + '. Proceeding with random testing.')
    query = pool[:n_tests]

# write query requests
query.to_csv(target)

print(f"{len(query.index)} requests queried from the pool of {len(pool.index)}, and saved to "+ target + '.')
