import os
from time import sleep

SERVICES = [
    'GitHub', 
    'Stripe_Products',
    'Yelp_Businesses',
    'YouTube_CommentsAndThreads',
    'YouTube_Videos',
    'YouTube_Search',
]

class Properties:
    def __init__(self, path):
        self.path = path

    def get(self, name):
        with open(self.path, "r") as f:
            properties = f.readlines()
        for property in properties:
            if '=' in property:
                key, value = property.split('=')
                if key.strip() == name:
                    return value.strip()

    def set(self, name, value):
        with open(self.path, "r") as f:
            properties = f.readlines()
        found = False
        for line, property in enumerate(properties):
            if '=' in property:
                key, old_value = property.split('=')
                if key.strip() == name:
                    properties[line] = name + "=" + str(value) + '\n'
                    found = True
                    break

        if not found:
            raise Exception(f'Property "{name}" not found in file {self.path}')

        with open(self.path, "w") as f:
            f.writelines(properties)


for mode in ['atlas', 'random', 'fuzzing']:
    for exp in range(10):
        for service in SERVICES:

            properties = Properties(f'src/test/resources/{service}/props.properties')
            properties.set('experiment.name', f'{service}_{mode}_{exp}')

            if mode=='atlas':
                properties.set('generator', 'MLT')
                properties.set('ml.learning.strategy', 'active')
            if mode=='random':
                properties.set('generator', 'MLT')
                properties.set('ml.learning.strategy', 'random')
            if mode=='fuzzing':
                properties.set('generator', 'RT')

            print(f'{service}_{mode}_{exp}')
            os.system(f'java -jar restest-full.jar src/test/resources/{service}/props.properties')

        sleep(86400)
            
