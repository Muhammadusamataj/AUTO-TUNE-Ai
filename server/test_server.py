import importlib.util
import json
import os
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

spec=importlib.util.spec_from_file_location("api", os.path.join(os.path.dirname(__file__), "server.py"))
api=importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)

class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory()
        api.DB_PATH=os.path.join(cls.temp.name,"test.sqlite3")
        api.KEY="testing-only-token"
        with api.db_connect() as db: pass
        cls.http=ThreadingHTTPServer(("127.0.0.1",0),api.Handler)
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True)
        cls.thread.start()
        cls.base=f"http://127.0.0.1:{cls.http.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown(); cls.http.server_close(); cls.thread.join(timeout=5)
        cls.temp.cleanup()

    def request(self, path, method="GET", obj=None, token=None):
        data=None if obj is None else json.dumps(obj).encode()
        headers={"Content-Type":"application/json"}
        if token is not None: headers["Authorization"]="Bearer "+token
        req=Request(self.base+path, data=data, method=method, headers=headers)
        try:
            with urlopen(req,timeout=3) as r: return r.status,json.loads(r.read())
        except HTTPError as e: return e.code,json.loads(e.read())

    def test_normalized_recording_and_distinct_remix(self):
        body={"title":"Café  Song (Official Video)","artist":" The Artist ","bands_db":[0]*5}
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],201)
        q=urlencode({"title":"Cafe\u0301 Song","artist":"the artist"})
        self.assertEqual(self.request("/v1/profile?"+q)[0],200)
        q=urlencode({"title":"Café Song (Remix)","artist":"the artist"})
        self.assertEqual(self.request("/v1/profile?"+q)[0],404)

    def test_youtube_style_metadata_matches_but_other_versions_do_not(self):
        body={"title":"Yellow","artist":"Coldplay","bands_db":[0,0,0,0,0]}
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],201)
        for title,artist in [("Coldplay - Yellow (Official Video) [4K]","Coldplay - Topic"),("Yellow | Official Music Video","COLDPLAY"),("Yellow (Lyrics)","Coldplay")]:
            self.assertEqual(self.request("/v1/profile?"+urlencode({"title":title,"artist":artist}))[0],200,(title,artist))
        for title in ["Yellow (Live)","Yellow (Remix)","Other - Yellow","Yellow (feat. X)"]:
            self.assertEqual(self.request("/v1/profile?"+urlencode({"title":title,"artist":"Coldplay"}))[0],404,title)

    def test_hostile_gain_types_and_nonfinite(self):
        for value in [True, "1", None, float("nan"), float("inf"), -3.1]:
            body={"title":"Hostile","artist":"test","bands_db":[0,0,0,0,value]}
            self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],400)

    def test_write_fails_closed_without_configured_key(self):
        original=api.KEY
        try:
            api.KEY=""
            body={"title":"T","artist":"A","bands_db":[0]*5}
            self.assertEqual(self.request("/v1/profile","POST",body,original)[0],403)
        finally:
            api.KEY=original

    def test_length_limits_and_source(self):
        body={"title":"x"*201,"artist":"A","bands_db":[0]*5}
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],400)
        body.update(title="T",source="fabricated AI")
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],400)
        body.update(title="x"*5000)
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],413)

    def test_missing_artist_rejected(self):
        self.assertEqual(self.request("/v1/profile?title=Song")[0],400)

    def test_huge_integer_gain_is_bad_request_not_server_error(self):
        body={"title":"huge gain","artist":"test","bands_db":[0,0,0,0,10**1000]}
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],400)

    def test_health(self):
        self.assertEqual(self.request("/health")[0],200)

    def test_unknown_returns_404(self):
        q=urlencode({"title":"not available", "artist":"demo"})
        self.assertEqual(self.request("/v1/profile?"+q)[0],404)

    def test_missing_api_key_rejected(self):
        self.assertEqual(self.request("/v1/profile", "POST", {"title":"x","artist":"y","bands_db":[0]*5})[0],403)

    def test_write_then_lookup(self):
        body={"title":"Original Test Tone", "artist":"AutoTune", "bands_db":[-0.2,0,0.5,0,0.1],"source":"user_test"}
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],201)
        q=urlencode({"title":"original test tone", "artist":"autotune"})
        status,found=self.request("/v1/profile?"+q)
        self.assertEqual(status,200)
        self.assertEqual(found["bands_db"],body["bands_db"])

    def test_bad_gain_rejected(self):
        body={"title":"bad", "artist":"test", "bands_db":[100,0,0,0,0]}
        self.assertEqual(self.request("/v1/profile","POST",body,"testing-only-token")[0],400)

    def test_list_payload_is_bad_request(self):
        self.assertEqual(self.request("/v1/profile","POST",[1,2,3],"testing-only-token")[0],400)

    def test_none_payload_is_bad_request(self):
        self.assertEqual(self.request("/v1/profile","POST",[None],"testing-only-token")[0],400)

    def test_unicode_token_is_rejected_not_server_error(self):
        self.assertEqual(self.request("/v1/profile","POST",{"title":"T","artist":"A","bands_db":[0]*5},"wrong-é")[0],403)

    def test_sql_injection_is_data(self):
        q=urlencode({"title":"' OR 1=1 --", "artist":"xx"})
        self.assertEqual(self.request("/v1/profile?"+q)[0],404)

if __name__=="__main__": unittest.main(verbosity=2)
