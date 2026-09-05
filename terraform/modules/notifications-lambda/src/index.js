'use strict';
// Placeholder handler. Not application code. Logs the SNS envelope shape so the subscription can
// be verified from CloudWatch and returns 200. Replaced by the Beacon team when BCN-240 lands.
exports.handler = async (event) => {
  const records = Array.isArray(event.Records) ? event.Records : [];
  for (const r of records) {
    const msg = r.Sns || {};
    console.log(JSON.stringify({
      level: 'info',
      msg: 'placeholder notifications target received event',
      messageId: msg.MessageId,
      subject: msg.Subject,
      attributes: Object.keys(msg.MessageAttributes || {}),
      environment: process.env.MERIDIAN_ENVIRONMENT,
    }));
  }
  return { statusCode: 200, body: JSON.stringify({ received: records.length }) };
};
