import yaml

def read_dict(path):
    with open(path, 'r') as f:
        return yaml.safe_load(f)
def write_dict(path, value):
    with open(path, 'w') as f:
        yaml.dump(value, f, sort_keys=False)
