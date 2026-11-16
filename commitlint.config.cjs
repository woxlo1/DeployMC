'use strict';

/**
 * Conventional Commits rules from the development specification, Part 12.3.
 * Scopes are recommended but not enforced, so repository automation and
 * component-specific scopes can be used without amending this file.
 */
module.exports = {
  rules: {
    'type-empty': [2, 'never'],
    'type-enum': [
      2,
      'always',
      [
        'feat',
        'fix',
        'docs',
        'spec',
        'test',
        'refactor',
        'perf',
        'build',
        'ci',
        'chore',
        'revert',
        'i18n',
        'security',
      ],
    ],
    'type-case': [2, 'always', 'lower-case'],
    'subject-empty': [2, 'never'],
    'subject-full-stop': [2, 'never', '.'],
    'header-max-length': [2, 'always', 72],
    'body-leading-blank': [2, 'always'],
    'body-max-line-length': [2, 'always', 120],
    'footer-leading-blank': [2, 'always'],
    'footer-max-line-length': [2, 'always', 120],
  },
};
