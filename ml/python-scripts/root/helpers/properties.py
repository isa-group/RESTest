from root.constants import RESTEST_PATH

class PropertiesFile:
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
                key, value = property.split('=')
                if key.strip() == name:
                    properties[line] = name + "=" + str(value) + "\n"
                    found = True
                    break

        if not found:
            raise Exception(f'Property "{name}" not found in file {self.path}')

        with open(self.path, "w") as f:
            f.writelines(properties)


