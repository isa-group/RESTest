import sys
import json
import joblib
import numpy as np
from sklearn.model_selection import cross_val_score

from root.constants import RESTEST_RESULTS_PATH
from root.data.dataset import read_dataset
from root.helpers.properties import PropertiesFile

if len(sys.argv) > 1:

    # path to the .properties file
    properties_file = sys.argv[1]

    # sampling ratio
    sampling_ratio = float(sys.argv[2])

else: # debug mode
    print('debug mode...')
    properties_file = '/home/giuliano/RESTest/src/test/resources/GitHub/props.properties'
    sampling_ratio = 1

# define the properties object
try:
    properties = PropertiesFile(properties_file)
except FileNotFoundError:
    raise Exception('Properties file '+ properties_file + 'does not exist.')

# path where to label test cases
experiment_folder = RESTEST_RESULTS_PATH + '/' + properties.get('experiment.name')
validation_data_path = experiment_folder

# load predictor
predictor_path = experiment_folder + '/predictor.joblib'
try:
    predictor = joblib.load(experiment_folder + '/predictor.joblib')
except Exception as e:
    raise Exception('Predictor not found in ' + predictor_path)

print("Validating predictor...")
print('Predictor path:        ' + predictor_path)
print('Validation data:       ' + validation_data_path)

try:
    # get train data
    train_data = read_dataset(validation_data_path, properties_file)
except Exception as e:
    raise Exception('training data folder "'+validation_data_path+'" not found.')

# preprocess train data
X_train = train_data.preprocess_requests()
y_train = train_data.obt_validities

# kfold cross validation:
if train_data.size < 30:
    out = {
        'accuracy': 0, 
        'roc_auc': 0
    }
else:
    out = {
        'accuracy': np.mean(cross_val_score(predictor, X_train, y_train, cv=5, scoring='accuracy')), 
        'roc_auc': np.mean(cross_val_score(predictor, X_train, y_train, cv=5, scoring='roc_auc'))
    }

with open(experiment_folder + '/validation_scores.json', 'w') as f:
    json.dump(out, f, sort_keys=False, indent=4)

# print output
print(f'The predictor was validated and scores were saved to ' + experiment_folder + '/validation_scores.json')
