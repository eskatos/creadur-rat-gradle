<?xml version="1.0" encoding="UTF-8"?>
<!--***********************************************************
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 ***********************************************************-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="html" encoding="UTF-8" indent="yes" doctype-system="about:legacy-compat"/>

  <xsl:variable name="unapproved" select="sum(/rat-report/statistics/statistic[@name='Unapproved']/@count)"/>

  <xsl:template match="/rat-report">
    <html lang="en">
      <head>
        <meta charset="UTF-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>Apache Rat report</title>
        <xsl:call-template name="styles"/>
      </head>
      <body>
        <main>
          <h1>Apache Rat report</h1>
          <p class="meta">
            <xsl:text>Generated </xsl:text>
            <xsl:value-of select="@timestamp"/>
            <xsl:text> by </xsl:text>
            <xsl:value-of select="version/@product"/>
            <xsl:text> </xsl:text>
            <xsl:value-of select="version/@version"/>
          </p>

          <xsl:call-template name="status"/>
          <xsl:call-template name="counters"/>
          <xsl:if test="$unapproved &gt; 0">
            <xsl:call-template name="unapproved-files"/>
          </xsl:if>
          <xsl:call-template name="licenses"/>
          <xsl:call-template name="files"/>
        </main>
      </body>
    </html>
  </xsl:template>

  <xsl:template name="styles">
    <style>
      :root {
        color-scheme: light dark;
        --bg: #f6f7f9;
        --surface: #ffffff;
        --text: #1c1e21;
        --muted: #5f6672;
        --line: #d9dde3;
        --pass-bg: #e3f5e8;
        --pass-fg: #1c6b34;
        --fail-bg: #fbe4e4;
        --fail-fg: #9c2323;
        --row: #f3f5f8;
      }
      @media (prefers-color-scheme: dark) {
        :root {
          --bg: #14161a;
          --surface: #1d2026;
          --text: #e7e9ec;
          --muted: #9aa3b0;
          --line: #333842;
          --pass-bg: #163a24;
          --pass-fg: #7fd79a;
          --fail-bg: #4a1d1d;
          --fail-fg: #ff9b9b;
          --row: #22262d;
        }
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        padding: 2rem 1.5rem 4rem;
        background: var(--bg);
        color: var(--text);
        font: 15px/1.5 -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      }
      main { max-width: 72rem; margin: 0 auto; }
      h1 { font-size: 1.75rem; margin: 0 0 0.25rem; }
      h2 { font-size: 1.2rem; margin: 2.5rem 0 0.75rem; }
      .meta { color: var(--muted); margin: 0; }
      .status {
        margin: 1.5rem 0;
        padding: 1rem 1.25rem;
        border-radius: 0.5rem;
        font-size: 1.15rem;
        font-weight: 600;
      }
      .status.pass { background: var(--pass-bg); color: var(--pass-fg); }
      .status.fail { background: var(--fail-bg); color: var(--fail-fg); }
      .status p { margin: 0; }
      .counters {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
        gap: 0.75rem;
      }
      .card {
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: 0.5rem;
        padding: 0.75rem 1rem;
      }
      .card .value { display: block; font-size: 1.6rem; font-weight: 600; line-height: 1.2; }
      .card .label { color: var(--muted); font-size: 0.85rem; }
      .card.fail { border-color: var(--fail-fg); }
      .card.fail .value { color: var(--fail-fg); }
      ul.unapproved-list { margin: 0; padding-left: 1.25rem; }
      ul.unapproved-list li { margin: 0.2rem 0; }
      code, .path {
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
        font-size: 0.9em;
      }
      .scroll {
        overflow-x: auto;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: 0.5rem;
      }
      table { border-collapse: collapse; width: 100%; }
      th, td {
        text-align: left;
        padding: 0.45rem 0.75rem;
        border-bottom: 1px solid var(--line);
        vertical-align: top;
        white-space: nowrap;
      }
      th { color: var(--muted); font-weight: 600; font-size: 0.85rem; }
      tbody tr:nth-child(even) { background: var(--row); }
      td.path { white-space: normal; word-break: break-all; min-width: 18rem; }
      td.num { text-align: right; }
      tr.unapproved td:first-child { border-left: 3px solid var(--fail-fg); }
      .approved { color: var(--pass-fg); }
      .unapproved-license { color: var(--fail-fg); font-weight: 600; }
      .pair { display: grid; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); gap: 1rem; }
      .muted { color: var(--muted); }
    </style>
  </xsl:template>

  <xsl:template name="status">
    <section>
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="$unapproved = 0">status pass</xsl:when>
          <xsl:otherwise>status fail</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <p id="audit-status">
        <xsl:choose>
          <xsl:when test="$unapproved = 0">No unapproved licenses</xsl:when>
          <xsl:when test="$unapproved = 1">1 unapproved license</xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="$unapproved"/>
            <xsl:text> unapproved licenses</xsl:text>
          </xsl:otherwise>
        </xsl:choose>
      </p>
    </section>
  </xsl:template>

  <xsl:template name="counters">
    <section class="counters">
      <xsl:for-each select="statistics/statistic">
        <div>
          <xsl:attribute name="class">
            <xsl:choose>
              <xsl:when test="@name = 'Unapproved' and $unapproved &gt; 0">card fail</xsl:when>
              <xsl:otherwise>card</xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
          <xsl:attribute name="title"><xsl:value-of select="@description"/></xsl:attribute>
          <span class="value">
            <xsl:if test="@name = 'Unapproved'">
              <xsl:attribute name="id">unapproved-count</xsl:attribute>
            </xsl:if>
            <xsl:value-of select="@count"/>
          </span>
          <span class="label"><xsl:value-of select="@name"/></span>
        </div>
      </xsl:for-each>
    </section>
  </xsl:template>

  <xsl:template name="unapproved-files">
    <section id="unapproved-files">
      <h2>Unapproved licenses</h2>
      <ul class="unapproved-list">
        <xsl:for-each select="resource[license/@approval = 'false']">
          <li>
            <span class="path"><xsl:value-of select="@name"/></span>
            <xsl:for-each select="license[@approval = 'false']">
              <xsl:text> </xsl:text>
              <span class="muted">(<xsl:value-of select="@name"/>)</span>
            </xsl:for-each>
          </li>
        </xsl:for-each>
      </ul>
    </section>
  </xsl:template>

  <xsl:template name="licenses">
    <section>
      <h2>Licenses</h2>
      <div class="pair">
        <xsl:call-template name="count-table">
          <xsl:with-param name="heading">License</xsl:with-param>
          <xsl:with-param name="rows" select="statistics/licenseName"/>
        </xsl:call-template>
        <xsl:call-template name="count-table">
          <xsl:with-param name="heading">Category</xsl:with-param>
          <xsl:with-param name="rows" select="statistics/licenseCategory"/>
          <xsl:with-param name="monospace" select="true()"/>
        </xsl:call-template>
      </div>
    </section>
  </xsl:template>

  <xsl:template name="count-table">
    <xsl:param name="heading"/>
    <xsl:param name="rows"/>
    <xsl:param name="monospace" select="false()"/>
    <div class="scroll">
      <table>
        <thead><tr><th><xsl:value-of select="$heading"/></th><th>Files</th></tr></thead>
        <tbody>
          <xsl:for-each select="$rows">
            <tr>
              <td>
                <xsl:choose>
                  <xsl:when test="$monospace"><code><xsl:value-of select="@name"/></code></xsl:when>
                  <xsl:otherwise><xsl:value-of select="@name"/></xsl:otherwise>
                </xsl:choose>
              </td>
              <td class="num"><xsl:value-of select="@count"/></td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table>
    </div>
  </xsl:template>

  <xsl:template name="files">
    <section>
      <h2>Files</h2>
      <div class="scroll">
        <table>
          <thead>
            <tr><th>File</th><th>Type</th><th>Media type</th><th>Encoding</th><th>License</th><th>Approval</th></tr>
          </thead>
          <tbody>
            <xsl:for-each select="resource">
              <tr>
                <xsl:if test="license/@approval = 'false'">
                  <xsl:attribute name="class">unapproved</xsl:attribute>
                </xsl:if>
                <td class="path"><xsl:value-of select="@name"/></td>
                <td><xsl:value-of select="@type"/></td>
                <td><xsl:value-of select="@mediaType"/></td>
                <td><xsl:value-of select="@encoding"/></td>
                <td>
                  <xsl:for-each select="license">
                    <xsl:call-template name="license-line-break"/>
                    <xsl:value-of select="@name"/>
                    <xsl:text> </xsl:text>
                    <code><xsl:value-of select="normalize-space(@family)"/></code>
                  </xsl:for-each>
                </td>
                <td>
                  <xsl:for-each select="license">
                    <xsl:call-template name="license-line-break"/>
                    <xsl:choose>
                      <xsl:when test="@approval = 'true'"><span class="approved">approved</span></xsl:when>
                      <xsl:otherwise><span class="unapproved-license">unapproved</span></xsl:otherwise>
                    </xsl:choose>
                  </xsl:for-each>
                </td>
              </tr>
            </xsl:for-each>
          </tbody>
        </table>
      </div>
    </section>
  </xsl:template>

  <xsl:template name="license-line-break">
    <xsl:if test="position() &gt; 1"><br/></xsl:if>
  </xsl:template>

</xsl:stylesheet>
