# Contributors attribution design

## Goal

Add clear project attribution for the GitHub maintainer and the AI tools used during development without representing AI products as GitHub accounts.

## README presentation

Add a `贡献者与致谢` section before the license section:

- [`@yuhold`](https://github.com/yuhold) — 作者、维护者与主要开发；Git 提交邮箱为 `yuhold.0757@qq.com`。
- [Claude](https://www.anthropic.com/claude)（Anthropic）— 开发、代码审查与文档辅助。
- [GPT](https://openai.com/)（OpenAI）— 开发辅助。

## GitHub contributor attribution

Future commits should use:

- Name: `yuhold`
- Email: `yuhold.0757@qq.com`

GitHub determines its Contributors graph from commits and cannot add Claude or GPT as synthetic GitHub users. Their acknowledgement therefore remains in README.

## Scope

This change only updates attribution documentation and local repository Git author configuration. It does not rewrite existing commit history, alter release assets, or create a new app version.
