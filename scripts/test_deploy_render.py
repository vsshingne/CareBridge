import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("deploy_render", Path(__file__).with_name("deploy-render.py"))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


def response(body):
    result = io.BytesIO(json.dumps(body).encode())
    result.status = 200
    return result


class DeploymentVerificationTest(unittest.TestCase):
    environment = {
        "RENDER_DEPLOY_HOOK": "https://api.render.com/deploy/srv-test?key=synthetic-secret",
        "PRODUCTION_API_ORIGIN": "https://api.example.test",
        "GITHUB_SHA": "a" * 40,
    }

    @patch.dict(os.environ, environment, clear=True)
    def test_requests_tested_commit_and_requires_matching_ready_revision(self):
        with patch.object(deploy.urllib.request, "urlopen", side_effect=[
            response({}), response({"status": "UP"}), response({"app": {"revision": "a" * 40}})
        ]) as opened, patch("builtins.print") as output:
            deploy.main()
        self.assertIn("ref=" + "a" * 40, opened.call_args_list[0].args[0].full_url)
        self.assertNotIn("synthetic-secret", str(output.call_args_list))

    @patch.dict(os.environ, environment, clear=True)
    def test_old_healthy_revision_is_not_deployment_success(self):
        with patch.object(deploy.urllib.request, "urlopen", side_effect=[
            response({}), response({"status": "UP"}), response({"app": {"revision": "b" * 40}})
        ]), patch.object(deploy.time, "monotonic", side_effect=[0, 1, 1000]), patch.object(deploy.time, "sleep"), patch("builtins.print"):
            with self.assertRaisesRegex(SystemExit, "timed out"):
                deploy.main()

    @patch.dict(os.environ, {}, clear=True)
    def test_missing_configuration_never_calls_network(self):
        with patch.object(deploy.urllib.request, "urlopen") as opened:
            with self.assertRaises(SystemExit):
                deploy.main()
            opened.assert_not_called()


if __name__ == "__main__":
    unittest.main()
