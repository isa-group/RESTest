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

else: # debug mode
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/GitHub/props.properties'

# define the properties object
try:
    properties = PropertiesFile(properties_file)
except FileNotFoundError:
    raise Exception('Properties file '+ properties_file + 'does not exist.')

# path where to label test cases
experiment_folder = RESTEST_RESULTS_PATH + '/' + properties.get('experiment.name')
target = experiment_folder + '/pool.csv'

# load predictor
predictor_path = experiment_folder + '/predictor.joblib'
try:
    predictor = joblib.load(experiment_folder + '/predictor.joblib')
except Exception as e:
    raise Exception('Predictor not found in ' + predictor_path)

print("Executing ml predictor...")
print('Predictor path:        ' + predictor_path)
print('Target requests:       ' + target)

try:
    # get pool data
    pool = read_raw(target)

    # remove duplicated indices
    pool = pool[~pool.index.duplicated()]
    initial_n_requests = len(pool.index)

except FileNotFoundError:
    raise Exception("pool data '"+target+"' not found.")

# transform train/pool data to tree form
X_pool = raw2preprocessed(pool, spec)

# subselect common features (todo: augment enum columns with missing values)
common_features = [f for f in predictor.features_names if f in X_pool.columns]
X_pool  = X_pool[common_features]

# scale data
scaler  = predictor.scaler
X_pool  = scaler.transform(X_pool)

# label the pool with predictions
predictions = pd.Series(predictor.predict(X_pool), index=pool.index)
pool = label_requests(pool, predictions)

# compute and sort predicted probabilities
probabilities = pd.Series(np.max(predictor.predict_proba(X_pool), axis=1), index=pool.index).sort_values(ascending=False)

# compute certainty threshold
certainty_threshold = predictor.certainty_threshold
print(f'certainty_threshold: {certainty_threshold}')

# select at least one index, then append more while class probability >= certainty_threshold
indices = [probabilities.index[0]]
probabilities = probabilities.drop(probabilities.index[0])
indices = indices + probabilities[probabilities >= certainty_threshold].index.tolist()

# subselect and sort pool rows
pool = pool.loc[indices]

# write query requests
pool.to_csv(target)

print(f"{initial_n_requests} requests labelled and reduced to {len(pool.index)}. {len(pool[pool['faulty']=='false'])} valid requests found.")
