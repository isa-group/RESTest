import numpy as np
from sklearn.model_selection import cross_val_score

# for dataset with a lower than threhsold size, the scores are 0
THRESHOLD = 50

def compute_scores(predictor, X_train, y_train):

    # compute number of valid and faulty requests
    n_valid  = y_train.tolist().count(True)
    n_faulty = y_train.tolist().count(False)
    n_minority = min(n_valid, n_faulty)
    
    if n_valid + n_faulty < THRESHOLD:
        return 0, 0

    # compute the number of folds
    n_folds = min(5, n_minority)

    # compute the metrics
    accuracy = np.mean(cross_val_score(predictor, X_train, y_train, cv=min(5), scoring='accuracy'))
    roc_auc  = np.mean(cross_val_score(predictor, X_train, y_train, cv=5, scoring='roc_auc'))

    return accuracy, roc_auc


