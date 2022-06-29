import sys

from root.constants import PREDICTOR, RESTEST_RESULTS_PATH, SCALER
from root.data.dataset import read_dataset
from root.helpers.properties import PropertiesFile
from root.helpers.resampling import resample
from root.helpers.scores import compute_scores

print(sys.argv)

args = ' '.join(sys.argv[1:])

# path to the .properties file
properties_file, resampling_ratio = args.split(' ')

# resampling ratio
resampling_ratio = float(resampling_ratio)

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

# if all responses were 401, 403, 413, or 429, score to be parsed by RESTest is 0
if train_data.size < 30:
    print(0)
else:
    # preprocess train data
    X_train = train_data.preprocess_requests()
    y_train = train_data.obt_validities

    # resample and compute certainty threshold
    X_train, y_train, certainty_threshold = resample(X_train, y_train, resampling_ratio)

    # scale data
    scaler  = SCALER
    X_train = scaler.fit_transform(X_train)

    # define and fit the predictor
    predictor = PREDICTOR
    predictor.fit(X_train, y_train)

    # kfold cross validation of the predictor:
    accuracy, roc_auc = compute_scores(predictor, X_train, y_train)    
    score = min(accuracy, roc_auc)

    # print score to be parsed by RESTest
    print(score)
