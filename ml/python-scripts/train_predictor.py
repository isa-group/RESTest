import sys
import joblib
import numpy as np
from imblearn.under_sampling import NearMiss
from sklearn.model_selection import cross_val_score

from root.constants import PREDICTOR, RESTEST_RESULTS_PATH, SCALER
from root.processing.dataset import read_dataset
from root.helpers.properties import PropertiesFile

if len(sys.argv) > 1:

    # path to the .properties file
    properties_file = sys.argv[1]

    # sampling ratio
    sampling_ratio = float(sys.argv[2])

else: # debug mode
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/GitHub/props.properties'
    sampling_ratio = 0.8

# define the properties object
try:
    properties = PropertiesFile(properties_file)
except FileNotFoundError:
    raise Exception('Properties file '+ properties_file + 'does not exist.')

# path where to find training data
experiment_folder = RESTEST_RESULTS_PATH + '/' + properties.get('experiment.name')
training_data_path = experiment_folder

# print('Training predictor...')
# print('Specification path:    ' + oas_path)
# print('Endpoint:              ' + endpoint)
# print('Operation:             ' + http_method)
# print('Training data path:    ' + training_data_path)
# print('Sampling ratio:        ' + str(sampling_ratio))

try:
    # get train data
    train_data = read_dataset(training_data_path, properties_file)
except FileNotFoundError:
    raise Exception('training data folder "'+training_data_path+'" not found.')

# preprocess train data
X_train = train_data.preprocess_requests()
y_train = train_data.obt_validities

# select features_names to later save them into predictor
features_names = X_train.columns.tolist()

# compute number of valid and faulty requests
n_valid  = y_train.tolist().count(True)
n_faulty = y_train.tolist().count(False)
n = n_valid + n_faulty

# perform sampling
if n_valid/n_faulty < sampling_ratio or n_faulty/n_valid < sampling_ratio:

    # define and execute sampler
    sampler = NearMiss(sampling_strategy=sampling_ratio, n_neighbors=max(1, int(0.1*n)))
    X_train, y_train = sampler.fit_resample(X_train, y_train)
    # print(f'X_train has {y_train.tolist().count(True)} valid and {y_train.tolist().count(False)} faulty requests.')

# compute certainty threshold
certainty_threshold = max(0.6, 0.75 - abs(0.25 - 0.25*(n_valid/n_faulty)))
# print(f'certainty_threshold: {certainty_threshold}')

# scale data
scaler  = SCALER
X_train = scaler.fit_transform(X_train)

# define and fit the predictor
predictor = PREDICTOR
predictor.fit(X_train, y_train)

# set certainty_threshold and scaler as predictor attributes
predictor.features_names = features_names
predictor.certainty_threshold = certainty_threshold
predictor.scaler = scaler

# save predictor
joblib.dump(predictor, training_data_path + '/predictor.joblib')

# kfold cross validation of the predictor:
if train_data.size < 50:
    accuracy = 0
    roc_auc  = 0
else:
    accuracy = np.mean(cross_val_score(predictor, X_train, y_train, cv=5, scoring='accuracy'))
    roc_auc  = np.mean(cross_val_score(predictor, X_train, y_train, cv=5, scoring='roc_auc'))

score = min(accuracy, roc_auc)

# print score to be parsed by RESTest
print(score)