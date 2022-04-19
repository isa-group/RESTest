import sys
import joblib
import numpy as np
from sklearn.model_selection import cross_val_score

from root.constants import PREDICTOR, RESTEST_RESULTS_PATH, SCALER
from root.processing.dataset import read_dataset
from root.helpers.properties import PropertiesFile
from root.processing.resampling import resample

if len(sys.argv) > 1:

    # path to the .properties file
    properties_file = sys.argv[1]

    # sampling ratio
    sampling_ratio = float(sys.argv[2])

else: # debug mode
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/Stripe_Products/props.properties'
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

# if all responses were 401, 403, 413, or 429, score to be parsed by RESTest is 0
if train_data.size == 0:
    print(0)
else:
    # preprocess train data
    X_train = train_data.preprocess_requests()
    y_train = train_data.obt_validities

    # select features_names to later save them into predictor
    features_names = X_train.columns.tolist()

    # resample and compute certainty threshold
    X_train, y_train, certainty_threshold = resample(X_train, y_train, sampling_ratio)

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
