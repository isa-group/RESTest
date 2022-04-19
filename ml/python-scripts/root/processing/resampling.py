from imblearn.under_sampling import NearMiss

def resample(X_train, y_train, sampling_ratio):

    # compute number of valid and faulty requests
    n_valid  = y_train.tolist().count(True)
    n_faulty = y_train.tolist().count(False)
    n_minority = min(n_valid, n_faulty)
    n_majority = max(n_valid, n_faulty)
    n = n_valid + n_faulty

    # perform sampling if more than one class present, and the minority ratio is less than the threshold
    if n_minority > 0 and n_minority/n_majority < sampling_ratio:

        # define and execute sampler
        sampler = NearMiss(sampling_strategy=sampling_ratio, n_neighbors=max(1, int(0.1*n)))
        X_train, y_train = sampler.fit_resample(X_train, y_train)
        # print(f'X_train has {y_train.tolist().count(True)} valid and {y_train.tolist().count(False)} faulty requests.')

    # compute certainty threshold
    certainty_threshold = max(0.6, 0.75 - abs(0.25 - 0.25*n_minority))
    
    return X_train, y_train, certainty_threshold
