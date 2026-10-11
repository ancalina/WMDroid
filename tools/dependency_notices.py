"""Exact dependency notices for source archives and runtime APK assets."""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_FILES = {
    'third_party/desugar_jdk_libs-2.1.5/LICENSE': '4b9abebc4338048a7c2dc184e9f800deb349366bdf28eb23c2677a77b4c87726',
    'third_party/desugar_jdk_libs-2.1.5/ADDITIONAL_LICENSE_INFO': 'a69bce275ba7a3570af6579cb0f55682cd75fedfcd49e0e8e9022270c447c916',
    'third_party/desugar_jdk_libs-2.1.5/ASSEMBLY_EXCEPTION': 'a44eb7b5caf5534c6ef536b21edb40b4d6babf91bf97d9d45596868618b2c6fb',
    'third_party/desugar_jdk_libs-2.1.5/LocalDate-NOTICE.txt': 'cd38b88d7358134a74d6d7b355bff58dc815d299fa8126b67824115ec8173014',
    'third_party/androidx_multidex-2.0.1/LICENSE': 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30',
    'third_party/androidx_multidex-2.0.1/NOTICE': 'e92734ddb0bb255c30cbb16c6a65eaee18fc01694ff149012abde58b8002d7db',
    'third_party/desugar_jdk_libs_configuration-2.1.5/LICENSE': '68834f116f8ff545f05d14753357b620748156d60ee36b26beab4cb3f317efe4',
    'third_party/dependencies.json': 'db18fc8fc732c7ed7c037c27e27adfc3783c224d39e2d8064d3eec2f633b51c6',
}
RUNTIME_FILES = SOURCE_FILES.copy()


def _validate(pins, name, data):
    expected = pins.get(name)
    if expected is None:
        return False
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Changed dependency notice payload: ' + name)
    return True


def validate_source(name, data):
    return _validate(SOURCE_FILES, name, data)


def validate_asset(name, data):
    return _validate(RUNTIME_FILES, name, data)


def _files(pins):
    result = {}
    for name in pins:
        path = ROOT / name
        parent = path
        while parent != ROOT:
            if parent.is_symlink():
                raise ValueError('Dependency notice symlink rejected: ' + name)
            parent = parent.parent
        data = path.read_bytes()
        _validate(pins, name, data)
        result[name] = data
    return result


def source_files():
    return _files(SOURCE_FILES)


def runtime_assets():
    return _files(RUNTIME_FILES)


def _self_test():
    sources, assets = source_files(), runtime_assets()
    assert len(sources) == 8 and len(assets) == 8
    assert 'third_party/desugar_jdk_libs_configuration-2.1.5/LICENSE' in sources
    assert 'third_party/desugar_jdk_libs_configuration-2.1.5/LICENSE' in assets
    for validate, files in ((validate_source, sources), (validate_asset, assets)):
        assert not validate('third_party/unknown/LICENSE', b'unknown')
        for name, data in files.items():
            assert validate(name, data)
            assert not validate('wrong-path/' + name, data)
            try:
                validate(name, data + b'changed')
            except ValueError:
                pass
            else:
                raise AssertionError('Changed notice accepted: ' + name)
    assert validate_asset('third_party/desugar_jdk_libs_configuration-2.1.5/LICENSE',
                              sources['third_party/desugar_jdk_libs_configuration-2.1.5/LICENSE'])
    print('PASS dependency notice pins, tamper rejection, wrong paths and runtime configuration license')


if __name__ == '__main__':
    _self_test()
