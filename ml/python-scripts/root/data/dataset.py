import os
import pandas as pd
from sklearn.metrics import accuracy_score, roc_auc_score
from root.helpers.spec import get_spec

# from root.processing.idl.dependencies import DEPENDENCIES
from root.data.processing import raw2preprocessed, read_raw

def read_dataset(path, properties_file):

    # read test cases and results
    if os.path.exists(path + "/requests.csv") and os.path.exists(path + "/responses.csv"):
        requests  = read_raw(path + "/requests.csv")
        responses = read_raw(path + "/responses.csv")
    else:
        requests_filenames  = [name for name in os.listdir(path) if name.startswith("test-cases")]
        responses_filenames = [name for name in os.listdir(path) if name.startswith("test-results")]
        if len(requests_filenames) > 0 and len(responses_filenames) > 0:
            requests  = pd.concat([read_raw(path + "/" + requests_path) for requests_path in requests_filenames])
            responses = pd.concat([read_raw(path + "/" + responses_path) for responses_path in responses_filenames])
        else:
            raise FileNotFoundError(f'No requests/responses found in {path}.')

    # filter irrelevant status codes
    responses = clean_status_codes(responses)
    requests = requests.loc[responses.index.values]

    return Dataset(requests, responses, properties_file)

class Dataset:
    def __init__(self, requests, responses, properties_file):
        self.requests        = requests
        self.responses       = responses
        self.properties_file = properties_file

    ### validities:
    @property
    def exp_validities(self):
        return self.requests['faulty'].apply(lambda x: not x)
    @property
    def obt_validities(self):
        return self.responses['statusCode'].apply(lambda x: is_a_valid_status_code(x))
    # @property
    # def true_validities(self):
    #     print('WARNING: USING TRUE VALIDITIES')
    #     tree_data = raw2preprocessed(self.requests, self.spec_path)
    #     dependencies = DEPENDENCIES[self.spec_path]
    #     out = tree_data.apply(lambda x: dependencies(x), axis = 1)
    #     return out

    ### others: 
    @property
    def size(self):
        return len(self.requests.index)

    def __getattribute__(self, name):

        ### exp/obt/true_valid/faulty_ratio:
        if name.endswith('_valid_ratio') or name.endswith('_faulty_ratio'):

            # if no requests, return 0
            if self.size == 0:
                return 0

            mode, validity = name.split('_')[:-1]

            V = self.__getattribute__('n_'+mode+'_valid')
            F = self.__getattribute__('n_'+mode+'_faulty')

            if validity == 'valid':
                return V/(V+F)
            else:
                return F/(V+F)

        ### exp/obt/true_valid(faulty):
        if name in ["exp_valid", "exp_faulty", "obt_valid", "obt_faulty", "true_valid", "true_faulty"]:
            mode = name.split("_")[0] + "_validities"
            validity = True if name.endswith("valid") else False
            validities = self.__getattribute__(mode)==validity
            return self.requests.loc[validities[validities==True].index]

        ### n_exp/obt/true_valid(faulty):
        if name.startswith("n_"):
            return len(self.__getattribute__(name[2:]))

        ### exp_vs_true_AUC:
        if any([name.startswith(x) for x in ["exp_vs_true", "exp_vs_obt", "obt_vs_true"]]):
            mode1, _, mode2 = name.split("_")[:3]
            validities1 = self.__getattribute__(mode1 + "_validities")
            validities2 = self.__getattribute__(mode2 + "_validities")
            if name.endswith("_AUC"):
                try:
                    return roc_auc_score(validities2, validities1)
                except ValueError as e: # when only one class is present
                    return accuracy_score(validities2, validities1)
            return accuracy_score(validities2, validities1)
        return object.__getattribute__(self, name)

    ### raw2preprocessed interface
    def preprocess_requests(self):
        return raw2preprocessed(self.requests, self.properties_file)

    # def get_simplified_test_cases(self):
    #     simplified_test_cases = self.responses.copy()
    #     simplified_test_cases["exp_validity"]  = self.exp_validities
    #     simplified_test_cases["obt_validity"]  = self.obt_validities
    #     simplified_test_cases["true_validity"] = self.true_validities
    #     return simplified_test_cases

    # def get_errors(self):
    #     simplified_test_cases = self.get_simplified_test_cases()
    #     out = simplified_test_cases.apply(lambda x: analyze_test_case(x), axis=1)
    #     out.name="errors"
    #     return out

    # def get_scores(self):
    #     errors = self.get_errors()
    #     scores = errors.value_counts()
    #     scores.index.name = "scores"
    #     return scores

    # def write_errors_analysis(self, output):
    #     self.get_simplified_test_cases().to_csv(output + '/simplified.csv')
    #     self.get_errors().to_csv(output                + '/errors.csv')
    #     self.get_scores().to_csv(output                + '/scores.csv')

    # def cbt_vs_idl(self):
    #     out = pd.DataFrame(index = self.requests.index, data = {'true': self.true_validities, 'exp': self.exp_validities})
    #     out = out[out['true']!=out['exp']]
    #     return out


# >>> Helpers: >>>
def clean_status_codes(responses):
    responses = responses.drop(responses[responses["statusCode"]==401].index) # missing or invalid credentials 
    responses = responses.drop(responses[responses["statusCode"]==403].index) # forbidden (insufficient api key permission) 
    responses = responses.drop(responses[responses["statusCode"]==413].index) # 
    responses = responses.drop(responses[responses["statusCode"]==429].index) # too many requests 
    return responses

def is_a_valid_status_code(x):
    x = int(x)
    is_faulty = x>=400 and x<500
    return not is_faulty

# def analyze_test_case(row):
#     exp_validity = row['exp_validity']
#     obt_validity = row['obt_validity']
#     true_validity = row['true_validity']

#     if row["statusCode"] >= 500:
#         return "5XX"
#     if "OAS" in row["failReason"]:
#         return "OAS"

#     out = 'IDL_'

#     if true_validity == exp_validity: # if the prediction is correct
#         out = out + 'T'
#     else:
#         out = out + 'F'

#     if obt_validity == exp_validity: # if the prediction is correct
#         out = out + 'N'
#     else:
#         out = out + 'P'

#     return out

# <<< Helpers: <<<


