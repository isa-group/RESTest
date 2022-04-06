import pandas as pd
import numpy as np
from sklearn.preprocessing import MinMaxScaler
from pandas.api.types import is_numeric_dtype
import datetime
from urllib.parse import unquote

# >>> Enum encoding >>>
def encode_enum(data, column):

    # 'presence' column
    data[column + '_presence'] = data[column].notna().astype(bool)

    # 'value' column with a fake value in empty columns
    data[column] = data[column].fillna('NONE').astype('category')

    # quote values (aesthetic)
    data[column] = data[column].apply(lambda x: "'" + x + "'" if x != 'NONE' else 'NONE')

    # one hot encoding
    data = pd.concat([data, pd.get_dummies(data[column], prefix=column, prefix_sep="==", dtype=bool)], axis=1)

    # drop original column that now is redundant
    data = data.drop(column, axis=1)

    return data
# <<< Enum encoding <<<

# >>> Dates encoding >>>
def encode_date(data, column):
    # EXAMPLE: publishedBefore=2016-10-04T23%3A50%3A37Z to 1475617837.0

    # 'presence' column
    data[column + '_presence'] = data[column].notna().astype(bool)

    # compute seconds passed from 01/01/1970
    data[column] = data[column].apply(convert_to_seconds_from_1970)

    return data

def convert_to_seconds_from_1970(x):
    try:
        return datetime.datetime.strptime(unquote(x), "%Y-%m-%dT%H:%M:%SZ").timestamp()
    except TypeError:
        return 0

# <<< Dates encoding <<<


# >>> Text encoding >>>
def encode_text(data, column):

    # 'presence' column
    data[column + '_presence'] = data[column].notna().astype(bool)

    # drop original column to reduce dimensionality
    data = data.drop(column, axis=1)

    return data
# <<< Text encoding <<<



# >>> Number encoding >>>
def encode_number(data, column):
    data[column]

    # 'presence' column
    data[column + '_presence'] = data[column].notna().astype(bool)

    # 'value' column with a 0 value in empty columns
    data[column + '_value'] = pd.to_numeric(data[column].fillna(0))

    # drop original column to reduce dimensionality
    data = data.drop(column, axis=1)

    return data
# <<< Number encoding <<<



# >>> Boolean encoding >>>
TRUE_VALUES = [
    'true', 
    'True', 
    '1', 
    1, 
]

FALSE_VALUES = [

    'false', 
    'False', 
    '0', 
    0, 
]

BOOLEAN_NAMES = TRUE_VALUES + FALSE_VALUES + ['']

def is_boolean_serie(serie):
    serie = serie.dropna()
    if not serie.empty:
        if all([x in BOOLEAN_NAMES for x in set(serie)]):
            return True
    return False

def convert_boolean_value(value):
    if value in TRUE_VALUES:
        return 'true'
    if value in FALSE_VALUES:
        return 'false'
    return value

def encode_boolean(tree_data, column):
    # tree_data[column] = tree_data[column].fillna('')
    tree_data[column] = tree_data[column].apply(convert_boolean_value)

    tree_data = encode_enum(tree_data, column)

    return tree_data
# <<< Boolean encoding <<<



# def max_min_scale(data):
#     number_columns = [column for column in data.columns if is_numeric_dtype(data[column])]
#     data[number_columns] = MinMaxScaler().fit_transform(data[number_columns])
#     return data
