import sys
import numpy as np
from sklearn.model_selection import cross_val_score
import pandas as pd

from root.constants import PREDICTOR, RESTEST_RESULTS_PATH, SCALER
from root.data.dataset import read_dataset
from root.data.processing import label_requests, raw2preprocessed, read_raw
from root.helpers.properties import PropertiesFile
from root.helpers.resampling import resample
from root.helpers.scores import compute_scores

if len(sys.argv) > 1:

    # path to the .properties file
    properties_file = sys.argv[1]

    # resampling ratio
    resampling_ratio = float(sys.argv[2])

else: # debug mode
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/GitHub/props.properties'
    resampling_ratio = 0.8

# define the properties file
try:
    properties = PropertiesFile(properties_file)
except FileNotFoundError:
    raise Exception('Properties file '+ properties_file + 'does not exist.')

# get the experiment folder
experiment_folder = RESTEST_RESULTS_PATH + '/' + properties.get('experiment.name')

# get train data
try:
    train_data = read_dataset(experiment_folder, properties_file)
except FileNotFoundError:
    raise Exception('training data folder "'+experiment_folder+'" not found.')

# get pool data
try:
    pool = read_raw(experiment_folder + '/pool.csv')

    # remove duplicated indices
    pool = pool[~pool.index.duplicated()]
    initial_n_requests = len(pool.index)

except FileNotFoundError:
    raise Exception('pool data '+ experiment_folder + '/pool.csv not found.')

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
probabilities = pd.Series(np.max(predictor.predict_proba(X_pool), axis=1), index=pool.index).sort_values(ascending=False)

# select at least one index, then append more while class probability >= certainty_threshold
indices = [probabilities.index[0]]
probabilities = probabilities.drop(probabilities.index[0])
indices = indices + probabilities[probabilities >= certainty_threshold].index.tolist()

# subselect and sort pool rows
pool = pool.loc[indices]

# write query requests
pool.to_csv(experiment_folder + '/pool.csv')

print(f"{initial_n_requests} requests labelled and reduced to {len(pool.index)}. {len(pool[pool['faulty']=='false'])} valid requests found.")
