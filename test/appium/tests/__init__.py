import asyncio
import logging
from datetime import datetime

from support.test_data import TestSuiteData


@asyncio.coroutine
def start_threads(quantity: int, func: type, returns: dict, *args):
    loop = asyncio.get_event_loop()
    for i in range(quantity):
        returns[i] = loop.run_in_executor(None, func, *args)
    for k in returns:
        returns[k] = yield from returns[k]
    return returns


def get_current_time():
    return datetime.now().strftime('%-m%-d%-H%-M%-S')


def debug(text: str):
    logging.debug(text)


test_suite_data = TestSuiteData()


basic_user = dict()
basic_user['password'] = "newuniquepassword12"
basic_user['passphrase'] = "tree weekend ceiling awkward universe pyramid glimpse raven pair lounge grant grief"
basic_user['username'] = "Little Weighty Iberianmole"
basic_user['public_key'] = "0x040d3400f0ba80b2f6017a9021a66e042abc33cf7051ddf98a24a815c93d6c052ce2b7873d799f096325" \
                           "9f41c5a1bf08133dd4f3fe63ea1cceaa1e86ebc4bc42c9"
basic_user['address'] = "f184747445c3b85ceb147dfb136067cb93d95f1d"

common_password = 'qwerty'
unique_password = 'unique' + get_current_time()

bootnode_address = "enode://a8a97f126f5e3a340cb4db28a1187c325290ec08b2c9a6b1f19845ac86c46f9fac2ba13328822590" \
                   "fd3de3acb09cc38b5a05272e583a2365ad1fa67f66c55b34@167.99.210.203:30404"
mailserver_address = "enode://531e252ec966b7e83f5538c19bf1cde7381cc7949026a6e499b6e998e695751aadf26d4c98d5a4eab" \
                     "fb7cefd31c3c88d600a775f14ed5781520a88ecd25da3c6:status-offline-inbox@35.225.227.79:30504"

camera_access_error_text = "To grant the required camera permission, please go to your system settings " \
                           "and make sure that Status > Camera is selected."

photos_access_error_text = "To grant the required photos permission, please go to your system settings " \
                           "and make sure that Status > Photos is selected."

connection_not_secure_text = "Connection is not secure! " \
                             "Do not sign transactions or send personal data on this site."
connection_is_secure_text = "Connection is secure. Make sure you really trust this site " \
                            "before signing transactions or entering personal data."
test_fairy_warning_text = "You are using an app installed from a nightly build. If you're connected to WiFi, " \
                          "your interactions with the app will be saved as video and logs. " \
                          "These recordings do not save your passwords. They are used by our development team " \
                          "to investigate possible issues and only occur if the app is install from a nightly build. " \
                          "Nothing is recorded if the app is installed from PlayStore or TestFlight."
