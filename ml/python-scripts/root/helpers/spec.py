import json
import yaml

from root.constants import RESTEST_PATH
from root.helpers.properties import PropertiesFile

def get_spec(properties_file):

    # define the properties object
    try:
        properties = PropertiesFile(properties_file)
    except FileNotFoundError:
        raise Exception('Properties file '+ properties_file + 'does not exist.')

    # get endpoint and http method
    with open(RESTEST_PATH + '/' + properties.get('conf.path'), 'r') as f:
        conf = yaml.safe_load(f)
    endpoint    = conf['testConfiguration']['operations'][0]['testPath']
    http_method = conf['testConfiguration']['operations'][0]['method']

    # get the path to oas
    oas_path = RESTEST_PATH + '/' + properties.get('oas.path')

    # load the service specification
    with open(oas_path, 'r') as f:
        if oas_path.endswith('json'):
            spec = json.load(f)
        elif oas_path.endswith('yaml'):
            spec = yaml.safe_load(f)

    types = {}
    descriptions = {}
    api_keys = []
    
    # version 2.0
    if not 'openapi' in spec.keys():
        parameters = spec['paths'][endpoint][http_method]['parameters']
        parameters = __correct_ref(parameters)

        for parameter in parameters:

            # types
            if parameter['type'] in ['enum', 'boolean'] or 'enum' in parameter.keys():
                types.update({parameter['name']: 'enum'})
            elif parameter['type'] in ['string', 'array']:
                types.update({parameter['name']: 'text'})
            else:
                types.update({parameter['name']: 'number'})

            # descriptions
            if 'description' in parameter.keys():
                descriptions.update({parameter['name']: __preprocess_description(parameter['description'])})
            else:
                descriptions.update({parameter['name']: ''})

    # version 3.0
    else:
        try:
            parameters = spec['paths'][endpoint][http_method]['parameters']
            parameters = __correct_ref(spec, parameters)

            # types
            for parameter in parameters:
                if 'enum' in parameter['schema'].keys() or 'anyOf' in parameter['schema'].keys() or parameter['schema']['type'] in ['bool', 'boolean']:
                    types.update({parameter['name']: 'enum'})
                elif parameter['schema']['type'] in ['array']:
                    types.update({parameter['name']: 'enum'})
                elif parameter['schema']['type'] in ['string']:
                    types.update({parameter['name']: 'text'})
                else:
                    types.update({parameter['name']: 'number'})

            # descriptions
            if 'description' in parameter.keys():
                descriptions.update({parameter['name']: __preprocess_description(parameter['description'])})
            else:
                descriptions.update({parameter['name']: ''})
    
        except Exception as e:

            parameters = spec['paths'][endpoint][http_method]['requestBody']['content']['application/x-www-form-urlencoded']['schema']['properties']

            # types
            for name, values in parameters.items():
                if 'anyOf' in values.keys() or 'enum' in values.keys():
                    types.update({name: 'enum'})
                elif values['type'] in ['bool', 'boolean']:
                    types.update({name: 'enum'})
                elif values['type'] in ['string', 'array']:
                    types.update({name: 'text'})
                else:
                    types.update({name: 'number'})

            # descriptions
            if 'description' in values.keys():
                descriptions.update({name: __preprocess_description(values['description'])})
            else:
                descriptions.update({name: ''})

    spec = {
        'api_keys': api_keys, 
        'types': types, 
        'descriptions': descriptions, 
    }

    return spec

def __correct_ref(spec, parameters):

    # correct '$ref' elements in specification
    for i, parameter in enumerate(parameters):
        if len(parameter.keys()) == 1 and '$ref' in parameter.keys():
            ref_paths = parameter['$ref'].split('/')[1:]
            aux_dict = spec

            for x in ref_paths:
                aux_dict = aux_dict[x]
            parameters[i] = aux_dict

    return parameters

def __preprocess_description(description):
    description = description.replace('\n', ' ')
    return description

# def read_spec(service):
#     with open(SPECS_PATH+ '/' + service + '.json', 'r') as f:
#         return json.load(f)

# def infer_service(path):
#     for service in ALL_SERVICES:
#         if service in path:
#             return service

# def write_spec(service, out_dir=SPECS_PATH):
#     endpoint, http_method = OAS_KEYS[service]

#     try:
#         api_keys = API_KEYS[service]
#     except:
#         api_keys = []

#     a_yaml_file = open(THIS_PATH + '/resources/openapis/'+service+'.yaml')
#     spec = yaml.safe_load(a_yaml_file)
#     if not 'openapi' in spec.keys():
#         parameters = spec['paths'][endpoint][http_method]['parameters']
#         types = {}
#         for parameter in parameters:
#             if parameter['type'] in ['enum', 'boolean'] or 'enum' in parameter.keys():
#                 types.update({parameter['name']: 'enum'})
#             elif parameter['type'] in ['string', 'array']:
#                 types.update({parameter['name']: 'text'})
#             else:
#                 types.update({parameter['name']: 'number'})
#             descriptions = {
#                 parameter['name']: __preprocess_description(parameter['description'])
#                 if 'description' in parameter.keys()
#                 else ''
#                 for parameter in parameters
#             }
#     else:
#         try:
#             parameters = spec['paths'][endpoint][http_method]['parameters']
#             parameters = correct_ref(parameters)

#             types = {}
#             for parameter in parameters:
#                 if 'enum' in parameter['schema'].keys() or 'anyOf' in parameter['schema'].keys() or parameter['schema']['type'] in ['bool', 'boolean']:
#                     types.update({parameter['name']: 'enum'})
#                 elif parameter['schema']['type'] in ['array']:
#                     types.update({parameter['name']: 'enum'})
#                 elif parameter['schema']['type'] in ['string']:
#                     types.update({parameter['name']: 'text'})
#                 else:
#                     types.update({parameter['name']: 'number'})
#             descriptions = {
#                 parameter['name']: __preprocess_description(parameter['description'])
#                 if 'description' in parameter.keys()
#                 else ''
#                 for parameter in parameters
#             }
#         except Exception:
#             parameters = spec['paths'][endpoint][http_method]['requestBody']['content'][
#                 'application/x-www-form-urlencoded'
#             ]['schema']['properties']
#             types = {}
#             for name, values in parameters.items():
#                 if 'anyOf' in values.keys() or 'enum' in values.keys():
#                     types.update({name: 'enum'})
#                 elif values['type'] in ['bool', 'boolean']:
#                     types.update({name: 'enum'})
#                 elif values['type'] in ['string', 'array']:
#                     types.update({name: 'text'})
#                 else:
#                     types.update({name: 'number'})
#             descriptions = {
#                 name: __preprocess_description(values['description'])
#                 if 'description' in values.keys()
#                 else ''
#                 for name, values in parameters.items()
#             }

#     spec = {
#         'api_keys': api_keys,
#         'types': types,
#         # 'descriptions': descriptions,
#     }

#     with open(out_dir + '/' + service + '.json', 'w') as f:
#         f.write(json.dumps(spec, indent=4, sort_keys=True))

# OAS_KEYS = {
#     'GitHub': ['/user/repos', 'get'],
#     'Foursquare': ['/venues/search', 'get'],
#     'Stripe_Coupons': ['/v1/coupons', 'post'],
#     'Stripe_Products': ['/v1/products', 'get'],
#     'Yelp_Businesses': ['/businesses/search', 'get'],
#     'YouTube_CommentsAndThreads': ['/youtube/v3/commentThreads', 'get'],
#     'YouTube_Videos': ['/youtube/v3/videos', 'get'],
#     'YouTube_Search': ['/youtube/v3/search', 'get'],
#     # 'YouTube_Search_local': ['/youtube/v3/search', 'get'],
#     # 'Ohsome_mock': ['/elements/area', 'get'],
#     # 'LanguageTool': ['/check', 'post'],
#     # 'LanguageTool_mod': ['/check', 'post'],
# }

# API_KEYS = {
#     'Foursquare': ['client_secret', 'client_id'],
#     'YouTube_CommentsAndThreads': ['key'],
#     'YouTube_Videos': ['key'],
#     'YouTube_Search': ['key'],
# }

