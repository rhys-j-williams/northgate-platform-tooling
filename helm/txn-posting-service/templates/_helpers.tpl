{{/*
Standard labels. app.kubernetes.io/* for the platform dashboards, meridian.bank/* for the CMDB
feed (CMDB-4410: the feed reads meridian.bank/app-id and nothing else, do not rename it).
*/}}
{{- define "txn-posting-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "txn-posting-service.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s" (include "txn-posting-service.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "txn-posting-service.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
app.kubernetes.io/name: {{ include "txn-posting-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: {{ .Values.partOf }}
meridian.bank/app-id: {{ .Values.cmdb.appId | quote }}
meridian.bank/owner: {{ .Values.cmdb.owner }}
meridian.bank/data-classification: {{ .Values.cmdb.dataClassification }}
meridian.bank/environment: {{ .Values.environment }}
{{- end -}}

{{- define "txn-posting-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "txn-posting-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "txn-posting-service.image" -}}
{{- printf "%s/%s:%s" .Values.image.registry .Values.image.repository (.Values.image.tag | default .Chart.AppVersion) -}}
{{- end -}}
