import pandas as pd
from urllib.parse import unquote

from root.helpers.spec import get_spec
from root.processing.encodings import encode_boolean, encode_datetime, encode_enum, encode_number, encode_text, is_boolean_serie

def raw2preprocessed(raw, properties_file):

    # get the service specification
    spec = get_spec(properties_file)

    # get service api keys and parameters types
    api_keys = spec['api_keys']
    types = spec['types']

    # subselect relevant columns
    raw = raw[['queryParameters', 'formParameters', 'bodyParameter', 'pathParameters']]

    # drop columns where all values are empty
    raw = raw.dropna(how='all', axis=1)
    if raw.empty:
        return pd.DataFrame()

    # fill non-empty columns
    raw = raw.fillna('')

    # define a new 'pre_encoding_data' dataset
    tree_data = pd.DataFrame()

    for testId, row in raw.iterrows():

        # define a new row
        new_preproc_row = pd.Series(name = testId)

        for column in raw.columns:
            key_value_pairs = row[column].split(';')[:-1]

            for key_value in key_value_pairs:
                key, value = key_value.split('=')

                # add key-value to the new row (if not an api key)
                if not key in api_keys:
                    new_preproc_row[key] = value

        # add the new row to the pre_encoding_data dataset
        tree_data = tree_data.append(new_preproc_row)

    # drop duplicated indices if any
    tree_data = tree_data.loc[tree_data.index.drop_duplicates(keep ='first')]

    # add missing columns (to match idl verification)
    # for column in types.keys():
    #     if not column in tree_data.columns:
    #         tree_data[column] = pd.Series()

    # change packagedimensions%5B%5D to packagedimensions[]
    tree_data = tree_data.rename(columns={x: unquote(x) for x in tree_data.columns})

    # encode & convert the dtype of each column according to its expected type
    for column in tree_data.columns:

        if column in types.keys():
            if types[column] == 'number':
                tree_data = encode_number(tree_data, column)

            if types[column] == 'datetime':
                tree_data = encode_datetime(tree_data, column)

            if types[column] == 'enum':
                if is_boolean_serie(tree_data[column]):
                    tree_data = encode_boolean(tree_data, column)
                else:
                    tree_data = encode_enum(tree_data, column)
            if types[column] == 'text':
                tree_data = encode_text(tree_data, column)
        else:
            # print(f'WARNING: {column} not found in {service} types. Treating feature as a text.') # only happening in Stripe_Products
            tree_data = encode_text(tree_data, column)

    return tree_data

def read_raw(raw_path):
    with open(raw_path, "r") as f:
        lines = f.readlines()
    lines = [l.replace(", but", "but") for l in lines]
    with open(raw_path, "w") as f:
        f.writelines(lines)
    raw = pd.read_csv(raw_path)
    if "testCaseId" in raw.columns:
        raw = raw.set_index("testCaseId")
    else:
        raw = raw.set_index("testResultId")
    return raw

def label_requests(raw, predictions):

    # label 'faulty' column
    raw.loc[predictions[predictions==False].index, 'faulty']='true'
    raw.loc[predictions[predictions==True].index,  'faulty']='false'

    # label 'faultyReason' column
    raw.loc[predictions[predictions==False].index, 'faultyReason']='inter_parameter_dependency'
    raw.loc[predictions[predictions==True].index,  'faultyReason']=None

    # label 'fulfillsDependencies' column
    raw.loc[predictions[predictions==False].index, 'fulfillsDependencies']='false'
    raw.loc[predictions[predictions==True].index,  'fulfillsDependencies']='true'

    return raw
