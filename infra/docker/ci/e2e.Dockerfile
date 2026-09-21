# This local-only alias is created by tools/server/prewarm-ci-images.sh after the
# official MCR image is verified against its pinned amd64 manifest.
FROM certmuse-ci/playwright:v1.55.0-noble-amd64-56816c8b

WORKDIR /workspace
RUN npm init --yes \
    && npm install --save-exact playwright@1.55.0

COPY tests/e2e/ /workspace/tests/e2e/
COPY docs/testing/knowledge-point-jsonl/ /workspace/docs/testing/knowledge-point-jsonl/
COPY web/admin/.env.production /workspace/web/admin/.env.production

CMD ["node", "/workspace/tests/e2e/run.mjs"]
