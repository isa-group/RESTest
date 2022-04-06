import os

def make_new_folder(path):
    i = 0
    new_folder = path + '/' + str(i)
    while os.path.exists(new_folder):
        i = i + 1
        new_folder = path + '/' + str(i)
    os.mkdir(new_folder)
    return new_folder
