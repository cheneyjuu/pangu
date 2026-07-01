#!/usr/bin/env node

import crypto from 'node:crypto'
import http from 'node:http'
import fs from 'node:fs'

const port = Number(process.env.FAKE_SMS_PORT || 19090)
const expectedBearer = process.env.FAKE_SMS_BEARER || ''
const signatureSecret = process.env.FAKE_SMS_SIGNATURE_SECRET || ''
const signatureHeader = process.env.FAKE_SMS_SIGNATURE_HEADER || 'x-pangu-signature'
const timestampHeader = process.env.FAKE_SMS_TIMESTAMP_HEADER || 'x-pangu-timestamp'
const logFile = process.env.FAKE_SMS_LOG_FILE || '/tmp/pangu-fake-sms-provider.jsonl'

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = ''
    req.setEncoding('utf8')
    req.on('data', chunk => {
      body += chunk
    })
    req.on('end', () => resolve(body))
    req.on('error', reject)
  })
}

function sendJson(res, status, payload) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(payload))
}

function validateSignature(req, body) {
  if (!signatureSecret) {
    return null
  }
  const timestamp = req.headers[timestampHeader.toLowerCase()]
  const signature = req.headers[signatureHeader.toLowerCase()]
  if (!timestamp || !signature) {
    return `missing signature headers ${timestampHeader}/${signatureHeader}`
  }
  const expected = crypto
    .createHmac('sha256', signatureSecret)
    .update(`${timestamp}\n${body}`)
    .digest('hex')
  if (signature !== expected) {
    return 'invalid signature'
  }
  return null
}

const server = http.createServer(async (req, res) => {
  if (req.method !== 'POST' || req.url !== '/sms') {
    sendJson(res, 404, { error: 'not found' })
    return
  }

  const body = await readBody(req)
  if (expectedBearer && req.headers.authorization !== `Bearer ${expectedBearer}`) {
    sendJson(res, 401, { error: 'invalid bearer token' })
    return
  }
  const signatureError = validateSignature(req, body)
  if (signatureError) {
    sendJson(res, 401, { error: signatureError })
    return
  }

  let payload
  try {
    payload = JSON.parse(body)
  } catch {
    sendJson(res, 400, { error: 'invalid json' })
    return
  }

  fs.appendFileSync(logFile, JSON.stringify({
    receivedAt: new Date().toISOString(),
    headers: req.headers,
    payload
  }) + '\n')

  sendJson(res, 200, {
    code: 0,
    data: {
      smsId: `fake-sms-${payload.deliveryId}`
    }
  })
})

server.listen(port, '127.0.0.1', () => {
  console.log(`[fake-sms] listening on http://127.0.0.1:${port}/sms`)
  console.log(`[fake-sms] logFile=${logFile}`)
})

process.on('SIGINT', () => {
  server.close(() => process.exit(0))
})
