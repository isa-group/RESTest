"""
Traverse an entire directory replacing a given set of keys in all found files.
"""

import os.path
import stat
import sys


def check_files(parent: str, files: list, keys: list, keyrepl: bytes):
    """
    Check a set of files in a directory.

    :param parent: directory path
    :param files: list of files in the directory
    :param keys: list of keys to replace
    :param keyrepl: replacement
    :return:
    """
    n = 0
    while True:
        # Find a name for temporary file where we will copy the
        # contents with possible modifications
        tmpname = os.path.join(parent, f".tmp{n}")
        if os.path.exists(tmpname):
            n += 1
        else:
            break
    for file in files:
        did_something = False
        fpath = os.path.join(parent, file)
        try:
            mode = os.lstat(fpath).st_mode
            # Skip non regular files.
            if not stat.S_ISREG(mode):
                continue
            with open(fpath, 'rb') as ifd, open(tmpname, 'wb') as ofd:
                for line in ifd:
                    initial = line
                    for key in keys:
                        line = line.replace(key, keyrepl)
                    if line != initial:
                        did_something = True
                    ofd.write(line)
            if did_something:
                # Replace the file with the modified one and copy
                # the original permission bits.
                os.rename(tmpname, fpath)
                os.chmod(fpath, stat.S_IMODE(mode))
            else:
                os.unlink(tmpname)
        except OSError as err:
            try:
                os.unlink(tmpname)
            except OSError:
                pass
            sys.stderr.write(f"Error at file '{fpath}': {err.strerror}.\n")


def handle_error(err: OSError):
    sys.stderr.write(f"Error at path '{err.filename}': {err.strerror}")


def main():
    """
    Read the keys, and traverse the top directory calling check_files
    for each subdirectory.
    :return:
    """
    if len(sys.argv) != 4:
        sys.stderr.write(
            f"Usage: {sys.argv[0]} keyfile directory replacement.\n")
        sys.exit(1)

    keyfile, topdir, replacement = sys.argv[1:]
    keys = []
    # Read keys from file
    try:
        with open(keyfile, 'rb') as kfd:
            for line in kfd:
                if line[-1:] == b'\n':
                    # Remove ending newline
                    keys.append(line[:-1])
                else:
                    keys.append(line)
    except OSError as err:
        sys.stderr.write(f"Error reading keys ({keyfile}): {err.strerror}.\n")
        return 1

    for pdir, _dirs, files in os.walk(topdir, onerror=handle_error):
        check_files(pdir, files, keys, replacement.encode('utf8'))

    return 0


if __name__ == "__main__":
    main()
